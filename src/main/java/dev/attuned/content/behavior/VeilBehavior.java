package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Veil Focus: patient stillness in low light hides the wearer until they move,
 * sprint, attack, take damage, or step into bright light.
 */
public final class VeilBehavior implements FocusBehavior {
	private static final int CHARGE_TICKS = 40;
	private static final int BREAK_COOLDOWN_TICKS = 60;
	private static final int ABILITY_COOLDOWN_TICKS = 300;
	private static final int ABILITY_VEIL_TICKS = 60;
	private static final int MAX_LIGHT = 7;
	private static final double MAX_STILL_SPEED_SQR = 0.003D;

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, Long> BLOCKED_UNTIL = new HashMap<>();

	public VeilBehavior() {
		AttunedPlayerCleanup.onForgetPlayer(VeilBehavior::forgetPlayer);
		AttunedServerCleanup.onStopServer(VeilBehavior::forgetAllPlayers);
	}

	/**
	 * Drops the veiled flag when the player dies (driven by UnseenCombat's death
	 * hook): the respawned entity is not invisible, and a stale flag would grant
	 * a free Needle opener on the first post-respawn swing before the next tick
	 * self-corrects.
	 */
	public static void forgetDeath(UUID id) {
		STATES.remove(id);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		State state = STATES.computeIfAbsent(id, ignored -> new State());
		long now = player.getLevel().getGameTime();
		if (!canCharge(player, now)) {
			clear(player, state, false);
			return;
		}
		state.chargeTicks++;
		if (state.chargeTicks >= CHARGE_TICKS && !state.veiled) {
			state.appliedInvisibility = !player.isInvisible();
			state.veiled = true;
			if (state.appliedInvisibility) {
				player.setInvisible(true);
			}
			veilFeedback(player, 0.85F);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		State state = STATES.remove(player.getUUID());
		if (state != null) {
			clear(player, state, false);
		}
	}

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return ABILITY_COOLDOWN_TICKS;
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		long now = player.getLevel().getGameTime();
		if (!canCharge(player, now)) {
			breakVeil(player);
			return false;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
		state.chargeTicks = CHARGE_TICKS;
		if (!state.veiled) {
			state.appliedInvisibility = !player.isInvisible();
			state.veiled = true;
			if (state.appliedInvisibility) {
				player.setInvisible(true);
			}
			veilFeedback(player, 0.85F);
		}
		player.addEffect(new MobEffectInstance(
			MobEffects.INVISIBILITY, ABILITY_VEIL_TICKS, 0, true, false, false));
		return true;
	}

	public static void breakVeil(ServerPlayer player) {
		breakVeil(player, BREAK_COOLDOWN_TICKS);
	}

	public static void breakVeil(ServerPlayer player, int cooldownTicks) {
		BLOCKED_UNTIL.put(player.getUUID(), player.getLevel().getGameTime() + cooldownTicks);
		State state = STATES.get(player.getUUID());
		if (state != null) {
			clear(player, state, true);
		}
	}

	public static boolean isVeiled(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.veiled;
	}

	private static void forgetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		State state = STATES.remove(id);
		BLOCKED_UNTIL.remove(id);
		if (state != null) {
			clear(player, state, false);
		}
	}

	private static void forgetAllPlayers(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			forgetPlayer(player);
		}
		STATES.clear();
		BLOCKED_UNTIL.clear();
	}

	private static boolean canCharge(ServerPlayer player, long now) {
		Long blockedUntil = BLOCKED_UNTIL.get(player.getUUID());
		if (blockedUntil != null && now < blockedUntil) {
			return false;
		}
		if (!player.isCrouching() || player.isSprinting()) {
			return false;
		}
		if (player.getDeltaMovement().horizontalDistanceSqr() > MAX_STILL_SPEED_SQR) {
			return false;
		}
		return player.getLevel().getMaxLocalRawBrightness(player.blockPosition()) <= MAX_LIGHT;
	}

	private static void clear(ServerPlayer player, State state, boolean broken) {
		state.chargeTicks = 0;
		if (!state.veiled) {
			return;
		}
		state.veiled = false;
		if (state.appliedInvisibility) {
			if (!player.hasEffect(MobEffects.INVISIBILITY)) {
				player.setInvisible(false);
			}
			state.appliedInvisibility = false;
		}
		if (broken) {
			veilFeedback(player, 1.25F);
		}
	}

	private static void veilFeedback(ServerPlayer player, float pitch) {
		ServerLevel level = (ServerLevel) player.getLevel();
		level.sendParticles(ParticleTypes.SMOKE,
			player.getX(), player.getY() + 0.9, player.getZ(), 10, 0.25, 0.45, 0.25, 0.01);
		level.playSound(null, player.blockPosition(),
			SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.45F, pitch);
	}

	private static final class State {
		private int chargeTicks;
		private boolean veiled;
		private boolean appliedInvisibility;
	}
}
