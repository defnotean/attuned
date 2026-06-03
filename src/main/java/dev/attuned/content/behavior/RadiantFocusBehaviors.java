package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.combat.CombatTargets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small Holy/Radiant behaviors that lean on vanilla conditions: light, bells,
 * candles, campfires, and deliberate defensive posture.
 */
public final class RadiantFocusBehaviors {
	private RadiantFocusBehaviors() {}

	private static final int BRIGHT_LIGHT = 12;
	private static final int DARK_LIGHT = 7;

	public static final class Votive implements FocusBehavior {
		private static final int CHECK_INTERVAL = 20;
		private static final int COOLDOWN_TICKS = 240;
		private static final int ABSORPTION_TICKS = 40;

		private final Map<UUID, Cooldown> state = new HashMap<>();

		public Votive() {
			AttunedPlayerCleanup.onForget(state::remove);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			Cooldown cooldown = state.computeIfAbsent(player.getUUID(), id -> new Cooldown());
			if (cooldown.remaining > 0) {
				cooldown.remaining--;
			}
			if (++cooldown.tick < CHECK_INTERVAL) {
				return;
			}
			cooldown.tick = 0;
			if (cooldown.remaining > 0 || !isBrightOrNearCandle(player) || player.hasEffect(MobEffects.ABSORPTION)) {
				return;
			}
			player.addEffect(new MobEffectInstance(
				MobEffects.ABSORPTION, ABSORPTION_TICKS, 0, true, false, true));
			cooldown.remaining = COOLDOWN_TICKS;
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			state.remove(player.getUUID());
		}
	}

	public static final class Bellwether implements FocusBehavior {
		private static final int CHECK_INTERVAL = 20;
		private static final int COOLDOWN_TICKS = 200;
		private static final int GLOW_TICKS = 80;
		private static final int BELL_RADIUS = 5;
		private static final double TARGET_RADIUS = 10.0;

		private final Map<UUID, Cooldown> state = new HashMap<>();

		public Bellwether() {
			AttunedPlayerCleanup.onForget(state::remove);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			Cooldown cooldown = state.computeIfAbsent(player.getUUID(), id -> new Cooldown());
			if (cooldown.remaining > 0) {
				cooldown.remaining--;
			}
			if (++cooldown.tick < CHECK_INTERVAL) {
				return;
			}
			cooldown.tick = 0;
			if (cooldown.remaining > 0 || !nearBlock(player, Blocks.BELL, BELL_RADIUS)) {
				return;
			}
			ServerLevel level = (ServerLevel) player.level();
			List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(TARGET_RADIUS),
				target -> target.isAlive() && CombatTargets.isHostileOrPvpOpponent(target, player)
					&& player.hasLineOfSight(target));
			for (LivingEntity target : targets) {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, true));
			}
			if (!targets.isEmpty()) {
				cooldown.remaining = COOLDOWN_TICKS;
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			state.remove(player.getUUID());
		}
	}

	public static final class Oathguard implements FocusBehavior {
		private static final int COOLDOWN_TICKS = 240;
		private static final int ABSORPTION_TICKS = 60;

		private final Map<UUID, Integer> cooldowns = new HashMap<>();

		public Oathguard() {
			AttunedPlayerCleanup.onForget(cooldowns::remove);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			UUID id = player.getUUID();
			int cooldown = Math.max(0, cooldowns.getOrDefault(id, 0) - 1);
			if (cooldown > 0) {
				cooldowns.put(id, cooldown);
				return;
			}
			cooldowns.remove(id);
			if (!player.isBlocking() || player.hasEffect(MobEffects.ABSORPTION)) {
				return;
			}
			player.addEffect(new MobEffectInstance(
				MobEffects.ABSORPTION, ABSORPTION_TICKS, 0, true, false, true));
			cooldowns.put(id, COOLDOWN_TICKS);
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			cooldowns.remove(player.getUUID());
		}
	}

	public static final class Censer implements FocusBehavior {
		private static final int CHECK_INTERVAL = 100;
		private static final int TRIM_TICKS = 20;

		private final Map<UUID, Integer> ticks = new HashMap<>();

		public Censer() {
			AttunedPlayerCleanup.onForget(ticks::remove);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			UUID id = player.getUUID();
			int tick = ticks.getOrDefault(id, 0) + 1;
			if (tick < CHECK_INTERVAL) {
				ticks.put(id, tick);
				return;
			}
			ticks.put(id, 0);
			if (!isBright(player) && !nearLitCampfire(player, 4, 2)) {
				return;
			}
			trim(player, MobEffects.POISON);
			trim(player, MobEffects.WITHER);
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			ticks.remove(player.getUUID());
		}

		private static void trim(ServerPlayer player, Holder<MobEffect> effect) {
			MobEffectInstance current = player.getEffect(effect);
			if (current == null) {
				return;
			}
			int remaining = current.getDuration() - TRIM_TICKS;
			player.removeEffect(effect);
			if (remaining > 0) {
				player.addEffect(new MobEffectInstance(
					effect, remaining, current.getAmplifier(), current.isAmbient(), current.isVisible(), true));
			}
		}
	}

	public static final class Threshold implements FocusBehavior {
		private static final int COOLDOWN_TICKS = 400;
		private static final int ABSORPTION_TICKS = 80;

		private final Map<UUID, ThresholdState> states = new HashMap<>();

		public Threshold() {
			AttunedPlayerCleanup.onForget(states::remove);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			ThresholdState state = states.computeIfAbsent(player.getUUID(),
				id -> new ThresholdState(player.level().getMaxLocalRawBrightness(player.blockPosition())));
			if (state.cooldown > 0) {
				state.cooldown--;
			}
			int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
			if (state.cooldown == 0 && state.lastLight <= DARK_LIGHT && light >= BRIGHT_LIGHT
					&& !player.hasEffect(MobEffects.ABSORPTION)) {
				player.addEffect(new MobEffectInstance(
					MobEffects.ABSORPTION, ABSORPTION_TICKS, 0, true, false, true));
				state.cooldown = COOLDOWN_TICKS;
			}
			state.lastLight = light;
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			states.remove(player.getUUID());
		}
	}

	private static boolean isBrightOrNearCandle(ServerPlayer player) {
		return isBright(player) || nearLitCandle(player, 4, 2);
	}

	private static boolean isBright(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) >= BRIGHT_LIGHT;
	}

	private static boolean nearLitCandle(ServerPlayer player, int radiusXz, int radiusY) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-radiusXz, -radiusY, -radiusXz),
				origin.offset(radiusXz, radiusY, radiusXz))) {
			BlockState state = player.level().getBlockState(pos);
			if (state.is(BlockTags.CANDLES) && AbstractCandleBlock.isLit(state)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearLitCampfire(ServerPlayer player, int radiusXz, int radiusY) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-radiusXz, -radiusY, -radiusXz),
				origin.offset(radiusXz, radiusY, radiusXz))) {
			BlockState state = player.level().getBlockState(pos);
			if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
					&& state.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearBlock(ServerPlayer player, net.minecraft.world.level.block.Block block, int radius) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-radius, -radius, -radius),
				origin.offset(radius, radius, radius))) {
			if (player.level().getBlockState(pos).is(block)) {
				return true;
			}
		}
		return false;
	}

	private static final class Cooldown {
		private int tick;
		private int remaining;
	}

	private static final class ThresholdState {
		private int lastLight;
		private int cooldown;

		private ThresholdState(int lastLight) {
			this.lastLight = lastLight;
		}
	}
}
