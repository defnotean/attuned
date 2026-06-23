package dev.attuned.pacts;

import dev.attuned.AttunedServerCleanup;
import dev.attuned.combat.CombatTargets;
import dev.attuned.combat.CombatFeedback;
import dev.attuned.combat.CombatMomentum;
import dev.attuned.combat.Resonance;
import dev.attuned.content.behavior.MaskBehavior;
import dev.attuned.network.ActionBarMessages;
import dev.attuned.network.FocusAbilityState;
import dev.attuned.network.FocusAbilityStatusPayload;
import dev.attuned.party.CircleContributions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pact micro-actives fired from the Focus Ability key when no ability Focus is equipped. */
public final class PactTacticals {
	private PactTacticals() {}

	public static final int COOLDOWN_TICKS = 600;
	/** Resonance spent when crouch-firing an overcharged tactical (drops most builds below Apex). */
	public static final float OVERCHARGE_SPEND = 0.25F;
	private static final int MAX_LIGHT = 7;
	private static final double UNTETHERED_KNOCKBACK = 0.35D;
	private static final double UNTETHERED_OVERCHARGE_KNOCKBACK = 0.55D;

	private static final Map<UUID, Long> COOLDOWN_ENDS = new HashMap<>();
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(PactTacticals::tick);
		AttunedServerCleanup.onStop(COOLDOWN_ENDS::clear);
	}

	/**
	 * Attempts to fire the player's pact tactical. Returns {@code true} when the attempt was
	 * handled (including cooldown feedback); {@code false} when gates fail and Apex may run.
	 */
	public static boolean tryTrigger(ServerPlayer player) {
		if (!Resonance.atApex(player)) {
			return false;
		}
		Optional<Pact> pact = Pacts.activeOf(player);
		if (pact.isEmpty()) {
			return false;
		}

		long now = player.level().getGameTime();
		Long endsAt = COOLDOWN_ENDS.get(player.getUUID());
		if (endsAt != null && now < endsAt) {
			int remaining = (int) (endsAt - now);
			ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
				Component.translatable(
					"pact.attuned.tactical.cooldown",
					FocusAbilityState.cooldownSecondsForMessage(remaining)));
			FocusAbilityState.syncStatus(player,
				FocusAbilityStatusPayload.PACT_TACTICAL_SLOT, remaining, COOLDOWN_TICKS);
			return true;
		}

		boolean overcharge = player.isCrouching() && Resonance.get(player) >= OVERCHARGE_SPEND;
		fireTactical(player, pact.get(), overcharge);
		if (overcharge) {
			Resonance.add(player, -OVERCHARGE_SPEND);
			ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,
				Component.translatable("pact.attuned.tactical.overcharge"));
		}
		CombatFeedback.pactTactical(player, pact.get(), overcharge);
		int cooldown = CombatMomentum.effectiveCooldown(
			COOLDOWN_TICKS, Resonance.killStreak(player), Resonance.atApex(player));
		COOLDOWN_ENDS.put(player.getUUID(), now + cooldown);
		FocusAbilityState.syncStatus(player,
			FocusAbilityStatusPayload.PACT_TACTICAL_SLOT, cooldown, cooldown);
		ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,
			Component.translatable(tacticalMessageKey(pact.get())));
		return true;
	}

	/** Shaves ticks off an in-flight tactical cooldown (kill-streak momentum). */
	public static void shaveCooldown(ServerPlayer player, int ticks) {
		if (ticks <= 0) {
			return;
		}
		Long endsAt = COOLDOWN_ENDS.get(player.getUUID());
		if (endsAt == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now >= endsAt) {
			COOLDOWN_ENDS.remove(player.getUUID());
			return;
		}
		long newEnds = Math.max(now, endsAt - ticks);
		if (newEnds <= now) {
			COOLDOWN_ENDS.remove(player.getUUID());
			FocusAbilityState.syncStatus(player,
				FocusAbilityStatusPayload.PACT_TACTICAL_SLOT, 0, COOLDOWN_TICKS);
		} else {
			COOLDOWN_ENDS.put(player.getUUID(), newEnds);
			int remaining = (int) (newEnds - now);
			FocusAbilityState.syncStatus(player,
				FocusAbilityStatusPayload.PACT_TACTICAL_SLOT, remaining, COOLDOWN_TICKS);
		}
	}

	public static int remainingCooldown(ServerPlayer player) {
		Long endsAt = COOLDOWN_ENDS.get(player.getUUID());
		if (endsAt == null) {
			return 0;
		}
		int remaining = (int) Math.max(0, endsAt - player.level().getGameTime());
		if (remaining <= 0) {
			COOLDOWN_ENDS.remove(player.getUUID());
			return 0;
		}
		return remaining;
	}

	private static void tick(MinecraftServer server) {
		pruneExpiredCooldowns(server.overworld().getGameTime());
	}

	private static void pruneExpiredCooldowns(long now) {
		COOLDOWN_ENDS.entrySet().removeIf(entry -> entry.getValue() <= now);
	}

	private static void fireTactical(ServerPlayer player, Pact pact, boolean overcharge) {
		ServerLevel level = (ServerLevel) player.level();
		double radiusScale = overcharge ? 1.5D : 1.0D;
		switch (pact) {
			case PYRESWORN -> firePyresworn(player, level, overcharge, radiusScale);
			case STONEHEART -> player.addEffect(new MobEffectInstance(
				MobEffects.DAMAGE_RESISTANCE, overcharge ? 80 : 40, overcharge ? 2 : 1, true, false, true));
			case WINDRUNNER -> player.addEffect(new MobEffectInstance(
				MobEffects.MOVEMENT_SPEED, overcharge ? 60 : 40, overcharge ? 2 : 1, true, false, true));
			case RADIANT_COVENANT -> glowHostiles(player, level, 6.0D * radiusScale, overcharge ? 90 : 60);
			case TIDESWORN -> slowHostiles(player, level, 5.0D * radiusScale, overcharge ? 60 : 40);
			case FORGEBOUND -> igniteHostiles(player, level, 4.0D * radiusScale, overcharge ? 3 : 2);
			case WILDROOT -> player.addEffect(new MobEffectInstance(
				MobEffects.REGENERATION, overcharge ? 100 : 60, overcharge ? 2 : 1, true, false, true));
			case NIGHTSWORN -> blindHostilesInDark(player, level, 4.0D * radiusScale, overcharge ? 40 : 20);
			case UNTETHERED -> knockbackHostiles(player, level, 5.0D * radiusScale,
				overcharge ? UNTETHERED_OVERCHARGE_KNOCKBACK : UNTETHERED_KNOCKBACK);
		}
	}

	private static void firePyresworn(ServerPlayer player, ServerLevel level, boolean overcharge, double radiusScale) {
		int seconds = overcharge ? 4 : 2;
		List<LivingEntity> hostiles = nearbyHostiles(player, level, 4.0D * radiusScale);
		if (!hostiles.isEmpty()) {
			recordTacticalContribution(player, hostiles);
			for (LivingEntity target : hostiles) {
				target.setSecondsOnFire(seconds);
			}
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, overcharge ? 30 : 20, 0, true, false, true));
	}

	private static void glowHostiles(ServerPlayer player, ServerLevel level, double radius, int ticks) {
		List<LivingEntity> targets = visibleRevealTargets(player, level, radius);
		recordTacticalContribution(player, targets);
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, true));
		}
	}

	private static void slowHostiles(ServerPlayer player, ServerLevel level, double radius, int ticks) {
		List<LivingEntity> targets = nearbyHostiles(player, level, radius);
		recordTacticalContribution(player, targets);
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, true, false, true));
		}
	}

	private static void igniteHostiles(ServerPlayer player, ServerLevel level, double radius, int seconds) {
		List<LivingEntity> targets = nearbyHostiles(player, level, radius);
		recordTacticalContribution(player, targets);
		for (LivingEntity target : targets) {
			target.setSecondsOnFire(seconds);
		}
	}

	private static void blindHostilesInDark(ServerPlayer player, ServerLevel level, double radius, int ticks) {
		if (level.getMaxLocalRawBrightness(player.blockPosition()) > MAX_LIGHT) {
			return;
		}
		List<LivingEntity> targets = nearbyHostiles(player, level, radius);
		recordTacticalContribution(player, targets);
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, true, false, true));
		}
	}

	private static void knockbackHostiles(ServerPlayer player, ServerLevel level, double radius, double strength) {
		Vec3 origin = player.position();
		List<LivingEntity> targets = nearbyHostiles(player, level, radius);
		recordTacticalContribution(player, targets);
		for (LivingEntity target : targets) {
			Vec3 away = target.position().subtract(origin);
			double dx = away.x;
			double dz = away.z;
			if (dx * dx + dz * dz < 1.0E-4D) {
				dx = player.getRandom().nextDouble() - 0.5D;
				dz = player.getRandom().nextDouble() - 0.5D;
			}
			target.knockback(strength, -dx, -dz);
		}
	}

	private static void recordTacticalContribution(ServerPlayer player, List<LivingEntity> targets) {
		if (!targets.isEmpty()) {
			CircleContributions.recordContribution(player);
		}
	}

	private static List<LivingEntity> nearbyHostiles(ServerPlayer player, ServerLevel level, double radius) {
		AABB area = player.getBoundingBox().inflate(radius);
		return level.getEntitiesOfClass(LivingEntity.class, area, target ->
			target != player
				&& target.isAlive()
				&& CombatTargets.isHostileOrPvpOpponent(target, player));
	}

	private static List<LivingEntity> visibleRevealTargets(ServerPlayer player, ServerLevel level, double radius) {
		AABB area = player.getBoundingBox().inflate(radius);
		return level.getEntitiesOfClass(LivingEntity.class, area, target ->
			target != player
				&& target.isAlive()
				&& CombatTargets.isHostileOrPvpOpponent(target, player)
				&& !MaskBehavior.resistsReveal(target)
				&& player.hasLineOfSight(target));
	}

	private static String tacticalMessageKey(Pact pact) {
		return switch (pact) {
			case PYRESWORN -> "pact.attuned.tactical.pyresworn";
			case STONEHEART -> "pact.attuned.tactical.stoneheart";
			case WINDRUNNER -> "pact.attuned.tactical.windrunner";
			case RADIANT_COVENANT -> "pact.attuned.tactical.radiant_covenant";
			case TIDESWORN -> "pact.attuned.tactical.tidesworn";
			case FORGEBOUND -> "pact.attuned.tactical.forgebound";
			case WILDROOT -> "pact.attuned.tactical.wildroot";
			case NIGHTSWORN -> "pact.attuned.tactical.nightsworn";
			case UNTETHERED -> "pact.attuned.tactical.untethered";
		};
	}
}
