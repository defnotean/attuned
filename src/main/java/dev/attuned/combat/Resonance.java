package dev.attuned.combat;

import dev.attuned.AttunedAdvancements;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

	/** Killing a matchup-empowered mob fills the gauge by this much. */
	private static final float KILL_EMPOWERED_GAIN = 0.30F;
	/** Killing a matchup-neutral mob fills the gauge a little. */
	private static final float KILL_NEUTRAL_GAIN = 0.05F;
	/** Each point of damage you deal on an empowered matchup gains this much. */
	private static final float HIT_EMPOWERED_GAIN_PER_DAMAGE = 0.01F;
	/** A single neutralized-matchup hit taken drains the gauge by this much. */
	private static final float HIT_NEUTRALIZED_LOSS = 0.10F;
	/** Per-tick idle decay — gauge falls to zero in about 200 seconds at rest. */
	private static final float DECAY_PER_TICK = 0.00025F;
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

		ServerLivingEntityEvents.AFTER_DAMAGE.register(Resonance::afterDamage);
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
		AttunedAttachments.setResonance(player, Math.max(MIN, Math.min(MAX, value)));
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
			if (attackerState.isAt(Apex.Capstone.MAELSTROM)
					&& hasAffinityPressure(defender, defenderState)) {
				add(attackerPlayer, dealtDamage * HIT_EMPOWERED_GAIN_PER_DAMAGE);
			} else if (matchup(attackerState, defender, defenderState) == Matchup.EMPOWERED) {
				add(attackerPlayer, dealtDamage * HIT_EMPOWERED_GAIN_PER_DAMAGE);
			}
		}
		// Defender side — drain when the matchup neutralizes us.
		if (defender instanceof Player defenderPlayer && attacker != null
				&& matchup(defenderState, attacker, attackerState) == Matchup.NEUTRALIZED) {
			add(defenderPlayer, -HIT_NEUTRALIZED_LOSS);
		}
		if (defender instanceof Player defenderPlayer && attacker != null
				&& defenderState.isAt(Apex.Capstone.STILLPOINT)
				&& hasAffinityPressure(attacker, attackerState)) {
			add(defenderPlayer, KILL_NEUTRAL_GAIN);
		}
	}

	/** AFTER_DEATH: large gain when a player finishes off a matchup-favoured mob. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
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
				add(player, KILL_EMPOWERED_GAIN);
				if (player instanceof ServerPlayer serverPlayer) {
					AttunedAdvancements.award(serverPlayer, "attunement/favored_matchup");
				}
			}
			case NORMAL -> {
				if (attackerState.isAt(Apex.Capstone.MAELSTROM)
						&& hasAffinityPressure(entity, targetState)) {
					add(player, KILL_EMPOWERED_GAIN);
				} else {
					add(player, KILL_NEUTRAL_GAIN);
				}
			}
			case NEUTRALIZED -> { /* No gain from a neutralized kill. */ }
		}
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
				set(player, current - DECAY_PER_TICK * 20);
			}
		}
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
