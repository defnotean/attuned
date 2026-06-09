package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.CombatTargets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Small Holy/Radiant behaviors that lean on vanilla conditions: light, bells,
 * candles, campfires, and deliberate defensive posture.
 */
public final class RadiantFocusBehaviors {
	private RadiantFocusBehaviors() {}

	private static final int BRIGHT_LIGHT = 12;
	private static final int DARK_LIGHT = 7;

	public static final class Votive implements FocusBehavior {
		private static final int COOLDOWN_TICKS = 240;
		private static final int ABSORPTION_TICKS = 40;

		private static final Map<UUID, Cooldown> STATE = new HashMap<>();
		private static boolean initialized;

		public Votive() {
			initLifecycle();
		}

		@Override
		public void onActivate(ServerPlayer player, ItemStack focus) {
			STATE.computeIfAbsent(player.getUUID(), id -> new Cooldown());
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			Cooldown cooldown = STATE.computeIfAbsent(player.getUUID(), id -> new Cooldown());
			if (cooldown.remaining > 0) {
				cooldown.remaining--;
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			STATE.remove(player.getUUID());
		}

		private static void initLifecycle() {
			if (initialized) {
				return;
			}
			initialized = true;
			AttunedPlayerCleanup.onForget(STATE::remove);
			AttunedServerCleanup.onStop(STATE::clear);
			ServerLivingEntityEvents.AFTER_DAMAGE.register(Votive::afterDamage);
		}

		private static void afterDamage(LivingEntity defender, DamageSource source,
				float originalDamage, float dealtDamage, boolean blocked) {
			if (dealtDamage <= 0.0F || !(defender instanceof ServerPlayer player)) {
				return;
			}
			Cooldown cooldown = STATE.get(player.getUUID());
			if (cooldown == null || cooldown.remaining > 0
					|| !isBrightOrNearCandle(player) || player.hasEffect(MobEffects.ABSORPTION)) {
				return;
			}
			LivingEntity attacker = AttunedCombat.attackerOf(source);
			if (attacker == null || attacker == defender
					|| !CombatTargets.isHostileOrPvpOpponent(attacker, player)) {
				return;
			}
			player.addEffect(new MobEffectInstance(
				MobEffects.ABSORPTION, ABSORPTION_TICKS, 0, true, false, true));
			cooldown.remaining = COOLDOWN_TICKS;
		}
	}

	public static final class Bellwether implements FocusBehavior {
		private static final int CHECK_INTERVAL = 20;
		private static final int COOLDOWN_TICKS = 200;
		private static final int GLOW_TICKS = 80;
		private static final int BELL_RADIUS = 8;
		private static final double TARGET_RADIUS = 10.0;

		private final Map<UUID, Cooldown> state = new HashMap<>();

		public Bellwether() {
			AttunedPlayerCleanup.onForget(state::remove);
			AttunedServerCleanup.onStop(state::clear);
			UseBlockCallback.EVENT.register(this::useBlock);
		}

		@Override
		public void onActivate(ServerPlayer player, ItemStack focus) {
			state.computeIfAbsent(player.getUUID(), id -> new Cooldown());
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
			revealVisibleThreats(player, cooldown);
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			state.remove(player.getUUID());
		}

		private InteractionResult useBlock(Player player, Level level,
				InteractionHand hand, BlockHitResult hitResult) {
			if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
					|| !level.getBlockState(hitResult.getBlockPos()).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}
			Cooldown cooldown = state.get(serverPlayer.getUUID());
			if (cooldown == null || cooldown.remaining > 0) {
				return InteractionResult.PASS;
			}
			revealVisibleThreats(serverPlayer, cooldown);
			return InteractionResult.PASS;
		}

		private void revealVisibleThreats(ServerPlayer player, Cooldown cooldown) {
			ServerLevel level = (ServerLevel) player.level();
			List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(TARGET_RADIUS),
				target -> target.isAlive() && CombatTargets.isHostileOrPvpOpponent(target, player)
					&& !MaskBehavior.resistsReveal(target)
					&& player.hasLineOfSight(target));
			for (LivingEntity target : targets) {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, true));
			}
			if (!targets.isEmpty()) {
				cooldown.remaining = COOLDOWN_TICKS;
			}
		}
	}

	public static final class Oathguard implements FocusBehavior {
		private static final int COOLDOWN_TICKS = 240;
		private static final int ABSORPTION_TICKS = 60;

		private final Map<UUID, Integer> cooldowns = new HashMap<>();
		private final Set<UUID> active = new HashSet<>();

		public Oathguard() {
			AttunedPlayerCleanup.onForget(cooldowns::remove);
			AttunedPlayerCleanup.onForget(active::remove);
			AttunedServerCleanup.onStop(cooldowns::clear);
			AttunedServerCleanup.onStop(active::clear);
			ServerLivingEntityEvents.AFTER_DAMAGE.register(this::afterDamage);
		}

		@Override
		public void onActivate(ServerPlayer player, ItemStack focus) {
			active.add(player.getUUID());
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			active.add(player.getUUID());
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
			grantAbsorption(player);
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			active.remove(player.getUUID());
			cooldowns.remove(player.getUUID());
		}

		private void afterDamage(LivingEntity defender, DamageSource source,
				float originalDamage, float dealtDamage, boolean blocked) {
			if (dealtDamage <= 0.0F || !(defender instanceof ServerPlayer player)
					|| !active.contains(player.getUUID()) || player.hasEffect(MobEffects.ABSORPTION)
					|| cooldowns.getOrDefault(player.getUUID(), 0) > 0) {
				return;
			}
			LivingEntity attacker = AttunedCombat.attackerOf(source);
			if (attacker == null || attacker == defender
					|| !CombatTargets.isHostileOrPvpOpponent(attacker, player)) {
				return;
			}
			grantAbsorption(player);
		}

		private void grantAbsorption(ServerPlayer player) {
			player.addEffect(new MobEffectInstance(
				MobEffects.ABSORPTION, ABSORPTION_TICKS, 0, true, false, true));
			cooldowns.put(player.getUUID(), COOLDOWN_TICKS);
		}
	}

	public static final class Censer implements FocusBehavior {
		private static final int CHECK_INTERVAL = 100;
		private static final int TRIM_TICKS = 20;

		private final Map<UUID, Integer> ticks = new HashMap<>();

		public Censer() {
			AttunedPlayerCleanup.onForget(ticks::remove);
			AttunedServerCleanup.onStop(ticks::clear);
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
					effect, remaining, current.getAmplifier(), current.isAmbient(), current.isVisible(), current.showIcon()));
			}
		}
	}

	public static final class Namesake implements FocusBehavior {
		private static final Identifier LUCK_ID =
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "namesake_focus_luck");
		private static final double LUCK_BONUS = 1.0D;
		private static final double NAMED_ENTITY_RADIUS = 8.0D;

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (carriesNamedItem(player) || nearNamedNonPlayer(player)) {
				applyLuck(player);
			} else {
				removeLuck(player);
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			removeLuck(player);
		}

		private static boolean carriesNamedItem(ServerPlayer player) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (hasCustomName(player.getItemInHand(hand))) {
					return true;
				}
			}
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				if (hasCustomName(inventory.getItem(slot))) {
					return true;
				}
			}
			return false;
		}

		private static boolean hasCustomName(ItemStack stack) {
			return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_NAME);
		}

		private static boolean nearNamedNonPlayer(ServerPlayer player) {
			ServerLevel level = (ServerLevel) player.level();
			return !level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(NAMED_ENTITY_RADIUS),
				target -> target.isAlive()
					&& !(target instanceof Player)
					&& target.hasCustomName()).isEmpty();
		}

		private static void applyLuck(ServerPlayer player) {
			AttributeInstance luck = player.getAttribute(Attributes.LUCK);
			if (luck == null || luck.getModifier(LUCK_ID) != null) {
				return;
			}
			luck.addTransientModifier(new AttributeModifier(
				LUCK_ID, LUCK_BONUS, AttributeModifier.Operation.ADD_VALUE));
		}

		private static void removeLuck(ServerPlayer player) {
			AttributeInstance luck = player.getAttribute(Attributes.LUCK);
			if (luck != null) {
				luck.removeModifier(LUCK_ID);
			}
		}
	}

	public static final class Threshold implements FocusBehavior {
		private static final int COOLDOWN_TICKS = 400;
		private static final int ABSORPTION_TICKS = 80;

		private final Map<UUID, ThresholdState> states = new HashMap<>();

		public Threshold() {
			AttunedPlayerCleanup.onForget(states::remove);
			AttunedServerCleanup.onStop(states::clear);
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
