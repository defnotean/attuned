package dev.attuned.combat;

import dev.attuned.compat.AfterDamageCallback;

import dev.attuned.Milestones;
import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.onboarding.Onboarding;
import dev.attuned.pacts.PactTacticals;
import dev.attuned.pacts.PactTier4;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Resonance — the engagement gauge that gates Apex.
 *
 * <p>A per-player float in {@code [0,1]} that fills when the player fights the
 * matchup their affinity beats, drains when they fight the matchup that beats
 * them, and slowly bleeds out at rest. Apex damage shaping (Execute, Unyielding,
 * Untouchable) only fires while resonance is at or above {@link #APEX_THRESHOLD}.
 *
 * <p>Solo players engaging matchup-favoured mobs sit near the top of the gauge
 * and feel their capstone almost continuously. A player dragged into protracted
 * unfavourable combat sees resonance drop and the capstone fade — keeping Apex
 * felt in the right fights and quiet in the wrong ones.</p>
 */
public final class Resonance {
	private Resonance() {}

	public static final float APEX_THRESHOLD = 0.50F;
	public static final float MAX = 1.0F;
	public static final float MIN = 0.0F;

	/** Killing a matchup-neutral mob fills the gauge a little. */
	private static final float KILL_NEUTRAL_GAIN = 0.05F;
	/** Rapid kills within this window extend the streak. */
	private static final int KILL_STREAK_WINDOW_TICKS = 200;
	private static final float KILL_STREAK_BONUS_PER = 0.015F;
	private static final float KILL_STREAK_BONUS_CAP = 0.12F;

	private static final Map<UUID, Long> LAST_KILL_TICK = new HashMap<>();
	private static final Map<UUID, Integer> KILL_STREAK = new HashMap<>();
	/** Pause idle decay briefly after the last combat resonance event. */
	private static final Map<UUID, Long> LAST_COMBAT_TICK = new HashMap<>();
	private static final int COMBAT_GRACE_TICKS = 100;
	private static boolean initialized;

	private record PlayerCombatState(Optional<Affinity> committed, boolean discord,
			Optional<Apex.Capstone> capstone) {
		boolean isAt(Apex.Capstone target) {
			return capstone.filter(value -> value == target).isPresent();
		}

		boolean hasAffinityPressure() {
			return committed.isPresent() || discord;
		}
	}

	/** Registers the event hooks and the per-tick decay. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		AttunedServerCleanup.onStop(() -> {
			LAST_KILL_TICK.clear();
			KILL_STREAK.clear();
			LAST_COMBAT_TICK.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			LAST_KILL_TICK.remove(uuid);
			KILL_STREAK.remove(uuid);
			LAST_COMBAT_TICK.remove(uuid);
		});

		AfterDamageCallback.EVENT.register(Resonance::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Resonance::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(Resonance::tick);
	}

	/** The player's current resonance value, in {@code [0, 1]}. */
	public static float get(Player player) {
		return AttunedAttachments.getResonance(player);
	}

	/** Whether the player is at or above the Apex gating threshold. */
	public static boolean atApex(Player player) {
		return get(player) >= APEX_THRESHOLD;
	}

	/** Sets the player's resonance, clamped to {@code [0, 1]}. */
	public static void set(Player player, float value) {
		float before = get(player);
		float clamped = Math.max(MIN, Math.min(MAX, value));
		AttunedAttachments.setResonance(player, clamped);
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		float delta = clamped - before;
		if (before < APEX_THRESHOLD && clamped >= APEX_THRESHOLD) {
			Onboarding.tryResonanceArmedHint(serverPlayer);
			CombatFeedback.resonanceArmed(serverPlayer);
			Milestones.onApexArmed(serverPlayer);
		} else if (delta >= CombatFeedback.gainThreshold()) {
			CombatFeedback.resonanceGain(serverPlayer, delta, clamped);
		} else if (delta <= -CombatFeedback.drainThreshold()) {
			CombatFeedback.resonanceDrain(serverPlayer);
		}
	}

	/** Adds (or subtracts) a delta to the player's resonance, clamping at the bounds. */
	public static void add(Player player, float delta) {
		set(player, get(player) + delta);
	}

	/**
	 * Environmental resonance grant for a resonant surge — the same clamped fill
	 * as combat gains, exposed as a named hook so {@link ResonantSurges} feeds the
	 * gauge through this class rather than poking the attachment directly.
	 *
	 * @param player the player standing inside a live surge
	 * @param amount the (already surge-multiplied) resonance to grant
	 */
	public static void grantSurge(Player player, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		add(player, amount);
	}

	/** AFTER_DAMAGE: gain on empowered hits dealt, drain on neutralized hits taken. */
	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || AttunedCombat.isReflecting()) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == defender) {
			return;
		}
		PlayerCombatState defenderState = null;
		if (defender instanceof Player defenderPlayer) {
			defenderState = playerState(defenderPlayer);
		}
		PlayerCombatState attackerState = null;
		if (attacker instanceof Player attackerPlayer) {
			attackerState = playerState(attackerPlayer);
		}
		// Attacker side — gain when the matchup empowers us. Maelstrom uses a
		// generic combat path because Discord deliberately has no single lane.
		if (attacker instanceof Player attackerPlayer) {
			float hitEmpoweredGainPerDamage = AttunedConfig.get().resonanceHitEmpoweredGainPerDamage();
			if (attackerState.isAt(Apex.Capstone.MAELSTROM)
					&& hasAffinityPressure(defender, defenderState)) {
				add(attackerPlayer, dealtDamage * hitEmpoweredGainPerDamage);
				markCombat(attackerPlayer);
			} else if (matchup(attackerState, defender, defenderState) == Matchup.EMPOWERED) {
				add(attackerPlayer, dealtDamage * hitEmpoweredGainPerDamage);
				markCombat(attackerPlayer);
			}
		}
		// Defender side — drain when the matchup neutralizes us.
		if (defender instanceof Player defenderPlayer && attacker != null
				&& matchup(defenderState, attacker, attackerState) == Matchup.NEUTRALIZED) {
			add(defenderPlayer, -AttunedConfig.get().resonanceHitNeutralizedLoss());
			markCombat(defenderPlayer);
		}
		if (defender instanceof Player defenderPlayer && attacker != null
				&& defenderState.isAt(Apex.Capstone.STILLPOINT)
				&& hasAffinityPressure(attacker, attackerState)) {
			add(defenderPlayer, KILL_NEUTRAL_GAIN);
			markCombat(defenderPlayer);
		}
	}

	/** AFTER_DEATH: large gain when a player finishes off a matchup-favoured mob. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		// The player's own death is the strongest "streak over" signal: end the
		// rolling kill-streak window rather than letting it survive a respawn.
		if (entity instanceof ServerPlayer died) {
			resetKillStreak(died.getUUID());
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (!(attacker instanceof Player player) || entity == player) {
			return;
		}
		PlayerCombatState attackerState = playerState(player);
		PlayerCombatState targetState = entity instanceof Player targetPlayer
			? playerState(targetPlayer)
			: null;
		switch (matchup(attackerState, entity, targetState)) {
			case EMPOWERED -> {
				add(player, AttunedConfig.get().resonanceKillEmpoweredGain() + streakBonus(player));
				if (player instanceof ServerPlayer serverPlayer) {
					AttunedAdvancements.award(serverPlayer, "attunement/favored_matchup");
					recordKillStreak(serverPlayer);
					markCombat(serverPlayer);
					ResonantSurges.tryKillReward(serverPlayer);
				}
			}
			case NORMAL -> {
				if (attackerState.isAt(Apex.Capstone.MAELSTROM)
						&& hasAffinityPressure(entity, targetState)) {
					add(player, AttunedConfig.get().resonanceKillEmpoweredGain() + streakBonus(player));
				} else {
					add(player, KILL_NEUTRAL_GAIN);
				}
				if (player instanceof ServerPlayer serverPlayer) {
					recordKillStreak(serverPlayer);
					markCombat(serverPlayer);
					ResonantSurges.tryKillReward(serverPlayer);
				}
			}
			case NEUTRALIZED -> resetKillStreak(player.getUUID());
		}
	}


	private static float streakBonus(Player player) {
		return Math.min(KILL_STREAK_BONUS_CAP,
			KILL_STREAK.getOrDefault(player.getUUID(), 0) * KILL_STREAK_BONUS_PER);
	}

	private static void recordKillStreak(ServerPlayer player) {
		UUID id = player.getUUID();
		long now = player.getLevel().getGameTime();
		Long last = LAST_KILL_TICK.get(id);
		int streak = last != null && now - last <= KILL_STREAK_WINDOW_TICKS
			? KILL_STREAK.getOrDefault(id, 0) + 1
			: 1;
		LAST_KILL_TICK.put(id, now);
		KILL_STREAK.put(id, streak);
		CombatFeedback.resonanceKillStreak(player, streak);
		if (atApex(player)) {
			int shave = CombatMomentum.killShaveTicks(streak, true);
			if (shave > 0) {
				PactTacticals.shaveCooldown(player, shave);
				Apex.shaveIdentityCooldown(player, shave);
			}
		}
	}

	/** Current kill streak within the rolling window, for momentum and HUD. */
	public static int killStreak(Player player) {
		return KILL_STREAK.getOrDefault(player.getUUID(), 0);
	}

	private static void resetKillStreak(UUID id) {
		LAST_KILL_TICK.remove(id);
		KILL_STREAK.remove(id);
	}
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Batch idle decay: apply 20x the per-tick rate once every 20 ticks
			// instead of writing the synced attachment every tick. The average
			// drain — and so the ~200s rest-to-zero curve — is unchanged.
			if (player.tickCount % 20 != 0) {
				continue;
			}
			float current = get(player);
			if (current > 0.0F) {
				long now = player.getLevel().getGameTime();
				Long lastCombat = LAST_COMBAT_TICK.get(player.getUUID());
				if (lastCombat != null && now - lastCombat < COMBAT_GRACE_TICKS) {
					continue;
				}
				float decay = AttunedConfig.get().resonanceDecayPerTick() * 20
					* PactTier4.resonanceDecayMultiplier(player);
				set(player, current - decay);
			}
		}
	}

	private static void markCombat(Player player) {
		LAST_COMBAT_TICK.put(player.getUUID(), player.getLevel().getGameTime());
	}

	/** How a player's committed affinity fares against another combatant's. */
	private static Matchup matchup(PlayerCombatState player, LivingEntity other,
			PlayerCombatState otherState) {
		Optional<Affinity> mine = player.committed();
		if (mine.isEmpty()) {
			return Matchup.NORMAL;
		}
		Optional<Affinity> theirs = otherState != null
			? otherState.committed()
			: AttunedCombat.affinityOf(other);
		if (theirs.isEmpty()) {
			return Matchup.NORMAL;
		}
		Affinity me = mine.get();
		Affinity them = theirs.get();
		if (me.beats(them)) {
			return Matchup.EMPOWERED;
		}
		if (them.beats(me)) {
			return Matchup.NEUTRALIZED;
		}
		return Matchup.NORMAL;
	}

	private static boolean hasAffinityPressure(LivingEntity entity, PlayerCombatState state) {
		return state != null ? state.hasAffinityPressure() : CombatTargets.hasAffinity(entity);
	}

	private static PlayerCombatState playerState(Player player) {
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		List<Integer> activeSlots = resolution.activeSlots();
		List<Optional<Affinity>> orderedActiveAffinities = new ArrayList<>(activeSlots.size());
		Set<Affinity> activeAffinities = EnumSet.noneOf(Affinity.class);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int used = 0;
		for (int slot : activeSlots) {
			Optional<FocusDefinition> maybeDefinition = Attunement.definitionFor(player, inv.get(slot));
			if (maybeDefinition.isEmpty()) {
				orderedActiveAffinities.add(Optional.empty());
				continue;
			}
			FocusDefinition definition = maybeDefinition.get();
			used += Attunement.effectiveCost(definition, inv.get(slot));
			Optional<Affinity> affinity = definition.affinity();
			orderedActiveAffinities.add(affinity);
			affinity.ifPresent(activeAffinities::add);
		}
		Optional<Affinity> committed = activeAffinities.size() == 1
			? Optional.of(activeAffinities.iterator().next())
			: Optional.empty();
		boolean discord = activeAffinities.size() >= 2;
		return new PlayerCombatState(committed, discord,
			Apex.resolveCapstone(orderedActiveAffinities, used, Attunement.capacity(player)));
	}

	private enum Matchup { EMPOWERED, NORMAL, NEUTRALIZED }
}
