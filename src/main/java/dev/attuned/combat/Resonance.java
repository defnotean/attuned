package dev.attuned.combat;

import dev.attuned.AttunedAdvancements;
import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.Attunement;
import java.util.Optional;
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

	/** Registers the event hooks and the per-tick decay. */
	public static void init() {
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

	/** AFTER_DAMAGE: gain on empowered hits dealt, drain on neutralized hits taken. */
	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == defender) {
			return;
		}
		// Attacker side — gain when the matchup empowers us.
		if (attacker instanceof Player attackerPlayer
				&& matchup(attackerPlayer, defender) == Matchup.EMPOWERED) {
			add(attackerPlayer, dealtDamage * HIT_EMPOWERED_GAIN_PER_DAMAGE);
		}
		// Defender side — drain when the matchup neutralizes us.
		if (defender instanceof Player defenderPlayer && attacker != null
				&& matchup(defenderPlayer, attacker) == Matchup.NEUTRALIZED) {
			add(defenderPlayer, -HIT_NEUTRALIZED_LOSS);
		}
	}

	/** AFTER_DEATH: large gain when a player finishes off a matchup-favoured mob. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (!(attacker instanceof Player player) || entity == player) {
			return;
		}
		switch (matchup(player, entity)) {
			case EMPOWERED -> {
				add(player, KILL_EMPOWERED_GAIN);
				if (player instanceof ServerPlayer serverPlayer) {
					AttunedAdvancements.award(serverPlayer, "attunement/favored_matchup");
				}
			}
			case NORMAL -> add(player, KILL_NEUTRAL_GAIN);
			case NEUTRALIZED -> { /* No gain from a neutralized kill. */ }
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float current = get(player);
			if (current > 0.0F) {
				set(player, current - DECAY_PER_TICK);
			}
		}
	}

	/** How a player's committed affinity fares against another combatant's. */
	private static Matchup matchup(Player player, LivingEntity other) {
		Optional<Affinity> mine = Attunement.committedAffinity(player);
		if (mine.isEmpty()) {
			return Matchup.NORMAL;
		}
		Optional<Affinity> theirs = AttunedCombat.affinityOf(other);
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

	private enum Matchup { EMPOWERED, NORMAL, NEUTRALIZED }
}
