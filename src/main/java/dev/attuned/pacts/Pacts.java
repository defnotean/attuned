package dev.attuned.pacts;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.compat.ParticleCompat;

import dev.attuned.compat.AfterDamageCallback;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.CombatContext;
import dev.attuned.combat.CombatTargets;
import dev.attuned.content.behavior.MaskBehavior;
import dev.attuned.combat.MobAffinities;
import dev.attuned.combat.Resonance;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Pacts system: detection, activation announcement, per-tick effects and
 * damage hooks for the named set bonuses ({@link Pact}).
 *
 * <p>A player has at most one pact active at any moment. Each single-affinity pact
 * wakes on three or more active Foci of its lane with no other affinity:
 * <ul>
 *   <li><b>Pyresworn</b> (Fury) — charged melee hits ignite the target.</li>
 *   <li><b>Stoneheart</b> (Bastion) — all incoming damage is dulled.</li>
 *   <li><b>Windrunner</b> (Zephyr) — sustained Speed and a taller step height.</li>
 *   <li><b>Radiant Covenant</b> (Holy) — charged hits reveal threats; undead take extra.</li>
 *   <li><b>Tidesworn</b> (Tide) — melee hits briefly Slow the target.</li>
 *   <li><b>Forgebound</b> (Forge) — melee hits sear the target with a short ignite.</li>
 *   <li><b>Wildroot</b> (Verdant) — a slow passive Regeneration while the pact holds.</li>
 *   <li><b>Nightsworn</b> (Umbral) — incoming damage is dulled while standing in the dark.</li>
 *   <li><b>Untethered</b> — at least four distinct active affinities, the Manifold
 *       path. Beats any single-affinity threshold when both qualify.</li>
 * </ul>
 *
 * <p>Per-tick aura particles, sustained MobEffects for Windrunner and Wildroot, an
 * outgoing-damage hook for Untethered/Radiant Covenant, an incoming-damage hook for
 * Stoneheart/Nightsworn and an AFTER_DAMAGE hook for Pyresworn/Tidesworn/Forgebound
 * all branch on the result of {@link #activeOf}.</p>
 */
public final class Pacts {
	private Pacts() {}

	/** How many same-affinity Foci a single-affinity pact requires. */
	private static final int SINGLE_AFFINITY_THRESHOLD = 3;
	/** Apply the Windrunner speed buff every quarter-second; the effect lasts longer. */
	private static final int WINDRUNNER_TICK = 5;
	/** Particle aura interval. */
	private static final int AURA_TICK = 10;

	/** Stoneheart dulls incoming damage by this fraction. */
	private static final float STONEHEART_DAMPEN = 0.10F;
	/** Untethered amplifies damage against affinity-bearing targets. */
	private static final float UNTETHERED_AMPLIFY = 0.15F;
	/** Pyresworn melee hits at half charge or above ignite the target for three seconds. */
	private static final int PYRESWORN_IGNITE_SECONDS = 3;
	/** Pyresworn only ignites when the swing is at least half-charged. */
	private static final float PYRESWORN_CHARGED_SWING_THRESHOLD = 0.5F;
	/** Radiant Covenant asks for a deliberate melee strike before revealing. */
	private static final float RADIANT_COVENANT_SWING_THRESHOLD = 0.9F;
	/** Radiant Covenant reveals visible threats briefly instead of adding raw PvP power. */
	private static final int RADIANT_COVENANT_REVEAL_TICKS = 80;
	/** Radiant Covenant's modest Smite-flavored boost against hostile undead. */
	private static final float RADIANT_COVENANT_UNDEAD_BONUS = 0.10F;
	/** Tidesworn melee hits drag the target in the current — a brief Slowness I. */
	private static final int TIDESWORN_SLOW_TICKS = 40;
	/** Forgebound melee hits sear the target for a short ember (a milder Pyresworn burn). */
	private static final int FORGEBOUND_IGNITE_SECONDS = 2;
	/** Wildroot knits a slow Regeneration I while the pact holds; refreshed every tick window. */
	private static final int WILDROOT_REGEN_TICK = 40;
	/** Nightsworn shrugs off this fraction of incoming damage while standing in the dark. */
	private static final float NIGHTSWORN_DARK_DAMPEN = 0.10F;
	/** Light level at or below which Nightsworn counts as standing in the dark (matches the Umbral Foci). */
	private static final int NIGHTSWORN_MAX_LIGHT = 7;
	/** Time window for a Pyresworn challenge kill after Pact fire catches a hostile. */
	private static final int PYRESWORN_CHALLENGE_WINDOW_TICKS = 20 * 20;
	/** Final damage threshold for the Stoneheart heavy-hit challenge. */
	private static final float STONEHEART_CHALLENGE_DAMAGE = 8.0F;
	/** Sustained active Windrunner sprint distance needed for its challenge. */
	private static final double WINDRUNNER_CHALLENGE_DISTANCE = 128.0;
	/** Ignores teleports/launches while counting Windrunner sprint distance. */
	private static final double WINDRUNNER_MAX_DELTA_PER_TICK = 1.25;
	/**
	 * Untethered's Manifold gate after the 8-affinity promotion: at least
	 * {@link #UNTETHERED_MIN_DISTINCT} distinct active affinities with no single
	 * affinity stacked to {@link #UNTETHERED_MAX_PER_AFFINITY} or more. Requiring
	 * all eight would be unrealistic, so diversity (not a full sweep) wakes it.
	 */
	private static final int UNTETHERED_MIN_DISTINCT = 4;
	private static final int UNTETHERED_MAX_PER_AFFINITY = 3;

	private static final String PYRESWORN_CHALLENGE = "attunement/pact_pyresworn_challenge";
	private static final String STONEHEART_CHALLENGE = "attunement/pact_stoneheart_challenge";
	private static final String WINDRUNNER_CHALLENGE = "attunement/pact_windrunner_challenge";
	private static final String UNTETHERED_CHALLENGE = "attunement/pact_untethered_challenge";

	/**
	 * Permanent {@link Attributes#STEP_HEIGHT} modifier id used by Windrunner.
	 * Adds {@link #WINDRUNNER_STEP_BONUS} to the vanilla 0.6 baseline, letting a
	 * Windrunner step a full block — the tops of stairs, slabs and single-block
	 * rises — without ever leaving the ground.
	 */
	private static final ResourceLocation WINDRUNNER_STEP_MODIFIER_ID =
		new ResourceLocation("attuned", "windrunner_step");
	/** Step-height bonus added on top of the vanilla 0.6 baseline (0.6 + 0.5 = 1.1). */
	private static final double WINDRUNNER_STEP_BONUS = 0.5;

	/** Per-player pact as of last tick, for spotting on/off transitions. */
	private static final Map<UUID, Pact> pactState = new HashMap<>();
	/** Hostiles recently caught by Pyresworn Pact fire, keyed by victim UUID. */
	private static final Map<UUID, PyreswornFireMark> pyreswornFireMarks = new HashMap<>();
	/** Sustained active Windrunner sprint progress, keyed by player UUID. */
	private static final Map<UUID, WindrunnerRun> windrunnerRuns = new HashMap<>();
	/** Per-server tick counter — drives aura cadence and the Windrunner effect. */
	private static int ticks;
	private static boolean initialized;

	/** Registers tick handlers, damage hooks and the cleanup callback. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerTickEvents.END_SERVER_TICK.register(Pacts::tick);
		AfterDamageCallback.EVENT.register(Pacts::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Pacts::afterDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			Optional<Pact> joined = activeOf(player);
			joined.ifPresent(pact -> pactState.put(player.getUUID(), pact));
			// Reconcile the Windrunner step-height modifier on join. addPermanentModifier
			// persists in NBT, so a returning player may carry a stale modifier from a
			// previous session. Strip it unconditionally, then re-apply if the player
			// is still in Windrunner — making the on-join state deterministic.
			removeWindrunnerStepHeight(player);
			if (joined.filter(p -> p == Pact.WINDRUNNER).isPresent()) {
				applyWindrunnerStepHeight(player);
			}
		});
		AttunedServerCleanup.onStopServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				removeWindrunnerStepHeight(player);
			}
			ticks = 0;
			pactState.clear();
			pyreswornFireMarks.clear();
			windrunnerRuns.clear();
		});
		AttunedPlayerCleanup.onForgetPlayer(player -> {
			UUID id = player.getUUID();
			removeWindrunnerStepHeight(player);
			PactTier4.removeStoneheartToughness(player);
			pactState.remove(id);
			windrunnerRuns.remove(id);
			pyreswornFireMarks.entrySet().removeIf(entry -> entry.getValue().playerId().equals(id));
		});
		PactTacticals.init();
	}

	/** The pact a player has woken, if any. */
	public static Optional<Pact> activeOf(Player player) {
		return activeOf(activeAffinityCounts(player));
	}

	/** The pact represented by pre-resolved active affinity counts, if any. */
	public static Optional<Pact> activeOf(Map<Affinity, Integer> counts) {
		if (isManifoldSpread(counts)) {
			return Optional.of(Pact.UNTETHERED);
		}
		if (counts.size() == 1) {
			Map.Entry<Affinity, Integer> only = counts.entrySet().iterator().next();
			if (only.getValue() >= SINGLE_AFFINITY_THRESHOLD) {
				return Optional.of(Pact.ofAffinity(only.getKey()));
			}
		}
		return Optional.empty();
	}

	/**
	 * Untethered's Manifold gate: four or more distinct active affinities with no
	 * single affinity stacked three or more deep (which would read as a committed
	 * lane instead of a true mixed spread).
	 */
	private static boolean isManifoldSpread(Map<Affinity, Integer> counts) {
		if (counts.size() < UNTETHERED_MIN_DISTINCT) {
			return false;
		}
		for (int count : counts.values()) {
			if (count >= UNTETHERED_MAX_PER_AFFINITY) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Short UI hint for a build that is exactly one same-affinity active Focus
	 * away from a single-affinity pact.
	 */
	public static Optional<Component> previewOf(Player player) {
		EnumMap<Affinity, Integer> counts = activeAffinityCounts(player);
		Optional<Pact> pact = activeOf(counts);
		return previewOf(player, pact, Attunement.isDiscord(player), counts, remainingBudget(player));
	}

	/**
	 * The affinity one active Focus away from waking a pact, when budget allows the
	 * cheapest matching Focus. Empty when no such opportunity exists.
	 */
	public static Optional<Affinity> oneAwayAffinity(Player player) {
		EnumMap<Affinity, Integer> counts = activeAffinityCounts(player);
		if (activeOf(counts).isPresent() || Attunement.isDiscord(player) || counts.size() != 1) {
			return Optional.empty();
		}
		Map.Entry<Affinity, Integer> only = counts.entrySet().iterator().next();
		if (only.getValue() != SINGLE_AFFINITY_THRESHOLD - 1) {
			return Optional.empty();
		}
		Affinity affinity = only.getKey();
		if (remainingBudget(player) < cheapestFocusCost(player, affinity)) {
			return Optional.empty();
		}
		return Optional.of(affinity);
	}

	/**
	 * Short UI hint from pre-resolved affinity counts and remaining budget.
	 * The caller owns the player-state snapshot; this helper only performs the
	 * registry lookup needed to know the cheapest matching Focus cost.
	 */
	public static Optional<Component> previewOf(Player player, Optional<Pact> pact, boolean discord,
			Map<Affinity, Integer> counts, int remainingBudget) {
		if (pact.isPresent() || discord || counts.size() != 1) {
			return Optional.empty();
		}
		Map.Entry<Affinity, Integer> only = counts.entrySet().iterator().next();
		if (only.getValue() != SINGLE_AFFINITY_THRESHOLD - 1) {
			return Optional.empty();
		}
		Affinity affinity = only.getKey();
		if (remainingBudget < cheapestFocusCost(player, affinity)) {
			return Optional.empty();
		}
		Pact next = Pact.ofAffinity(affinity);
		return Optional.of(next.displayName().withStyle(next.chatColor(), ChatFormatting.BOLD)
			.append(Component.literal(" needs 1 " + affinityName(affinity) + " Focus")
				.withStyle(ChatFormatting.GRAY)));
	}

	/** Whether a player has woken exactly this pact. */
	public static boolean isAt(Player player, Pact pact) {
		return activeOf(player).filter(p -> p == pact).isPresent();
	}

	private static EnumMap<Affinity, Integer> activeAffinityCounts(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		EnumMap<Affinity, Integer> counts = new EnumMap<>(Affinity.class);
		for (int slot : Attunement.activeSlots(player)) {
			Attunement.definitionFor(player, inv.get(slot))
				.flatMap(FocusDefinition::affinity)
				.ifPresent(a -> counts.merge(a, 1, Integer::sum));
		}
		return counts;
	}

	private static int remainingBudget(Player player) {
		return Math.max(0, Attunement.capacity(player) - Attunement.used(player));
	}

	private static int cheapestFocusCost(Player player, Affinity affinity) {
		int cheapest = Integer.MAX_VALUE;
		var registry = player.getLevel().registryAccess().registryOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		for (FocusDefinition definition : registry) {
			if (definition.affinity().filter(a -> a == affinity).isPresent()) {
				cheapest = Math.min(cheapest, definition.cost());
			}
		}
		return cheapest;
	}

	private static String affinityName(Affinity affinity) {
		String lower = affinity.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/**
	 * Layers the Stoneheart dampen and the Untethered amplifier onto an incoming
	 * hit. Stoneheart applies when the defender is in pact; Untethered applies
	 * when the attacker is in pact and the defender carries an affinity.
	 */
	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		return adjustDamage(defender, source, amount, CombatContext.of(defender, source));
	}

	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,
			CombatContext context) {
		if (amount <= 0.0F || AttunedCombat.isReflecting()) {
			return amount;
		}
		// Stoneheart: a defender-side dampen on everything, no matchup gating.
		Pact defenderPact = defender instanceof Player defenderPlayer
			? activeOf(context.activeAffinityCounts(defenderPlayer)).orElse(null)
			: null;
		if (defenderPact == Pact.STONEHEART) {
			float before = amount;
			amount *= (1.0F - STONEHEART_DAMPEN);
			if (defender instanceof ServerPlayer stoneheartPlayer) {
				PactTrials.onStoneheartAbsorb(stoneheartPlayer, before - amount);
			}
		}
		// Nightsworn: a defender-side dampen that only holds while the wearer stands
		// in the dark — the shadow shelters them. Mirrors Stoneheart's shape but
		// conditioned on light level rather than always-on.
		if (defenderPact == Pact.NIGHTSWORN && defender instanceof Player darkPlayer
				&& isInDark(darkPlayer)) {
			float dampen = PactTier4.nightswornDarkDampen(darkPlayer);
			float before = amount;
			amount *= (1.0F - dampen);
			if (darkPlayer instanceof ServerPlayer nightswornPlayer) {
				PactTrials.onNightswornAbsorb(nightswornPlayer, before - amount);
			}
		}
		// Untethered: an attacker-side amplifier against any affinity-bearing foe.
		if (source.getEntity() instanceof Player attackerPlayer) {
			Pact attackerPact = activeOf(context.activeAffinityCounts(attackerPlayer)).orElse(null);
			if (attackerPact == Pact.UNTETHERED
					&& !context.isAt(attackerPlayer, Apex.Capstone.MAELSTROM)
					&& canAffectCombatTarget(attackerPlayer, defender)
					&& context.hasAffinityPressure(defender)) {
				amount *= (1.0F + UNTETHERED_AMPLIFY);
			}
			if (attackerPact == Pact.RADIANT_COVENANT
					&& !(defender instanceof Player)
					&& isHostile(defender)
					&& defender.getMobType() == MobType.UNDEAD
					&& AttunedCombat.isChargedDirectMelee(attackerPlayer, defender, source, RADIANT_COVENANT_SWING_THRESHOLD)) {
				amount *= (1.0F + PactTier4.radiantUndeadBonus(attackerPlayer));
			}
		}
		return amount;
	}

	/**
	 * AFTER_DAMAGE proc. Pyresworn ignites the target on an at-least-half-charged
	 * melee hit; Untethered paints a defender-colored dust burst on impact so the
	 * fifteen-percent kicker reads on screen.
	 */
	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || AttunedCombat.isReflecting()) {
			return;
		}
		LivingEntity livingAttacker = AttunedCombat.attackerOf(source);
		maybeAwardStoneheartChallenge(defender, livingAttacker, dealtDamage);
		if (!(livingAttacker instanceof Player attacker)) {
			return;
		}
		if (attacker == defender || !defender.isAlive()) {
			return;
		}
		Pact attackerPact = activeOf(attacker).orElse(null);
		if (attackerPact == Pact.PYRESWORN) {
			pyreswornIgnite(attacker, defender, source);
		}
		if (attackerPact == Pact.TIDESWORN) {
			tideswornSlow(attacker, defender, source);
		}
		if (attackerPact == Pact.FORGEBOUND) {
			forgeboundSear(attacker, defender, source);
		}
		if (attackerPact == Pact.UNTETHERED
				&& !Apex.isAt(attacker, Apex.Capstone.MAELSTROM)
				&& canAffectCombatTarget(attacker, defender)
				&& hasAffinityPressure(defender)) {
			// Mirror the adjustDamage amplifier suppression: a Discord-Apex (Maelstrom)
			// attacker's Untethered bonus is suppressed there, so it shows no sparkle here.
			untetheredImpactSparkle(defender);
		}
		if (attackerPact == Pact.RADIANT_COVENANT) {
			radiantCovenantReveal(attacker, defender, source);
		}
	}

	private static void radiantCovenantReveal(Player attacker, LivingEntity defender, DamageSource source) {
		if (!defender.isAlive() || !CombatTargets.isHostileOrPvpOpponent(defender, attacker)
				|| !AttunedCombat.isChargedDirectMelee(attacker, defender, source, RADIANT_COVENANT_SWING_THRESHOLD)
				|| MaskBehavior.resistsReveal(defender)
				|| isOwnPet(defender, attacker) || defender instanceof AbstractVillager) {
			return;
		}
		defender.addEffect(new MobEffectInstance(
			MobEffects.GLOWING, RADIANT_COVENANT_REVEAL_TICKS, 0, true, false, true));
		if (defender.getLevel() instanceof ServerLevel level) {
			level.sendParticles(ParticleCompat.dust(Affinity.HOLY.argb() & 0x00FFFFFF, 0.9F),
				defender.getX(), defender.getY() + defender.getBbHeight() * 0.65, defender.getZ(),
				5, 0.25, 0.25, 0.25, 0.0);
		}
		if (attacker instanceof ServerPlayer serverPlayer) {
			PactTrials.onRadiantReveal(serverPlayer);
		}
	}

	private static boolean isOwnPet(LivingEntity defender, Player attacker) {
		if (!(defender instanceof TamableAnimal pet)) {
			return false;
		}
		UUID ownerId = pet.getOwnerUUID();
		return ownerId != null && attacker.getUUID().equals(ownerId);
	}

	/** Pyresworn's fire-on-strike, gated to direct melee and to at-least-half-charged swings. */
	private static void pyreswornIgnite(Player attacker, LivingEntity defender, DamageSource source) {
		if (!isDirectMelee(attacker, source)) {
			return;
		}
		// Only on at-least-half-charged swings — discourages the most extreme spam-clicking.
		if (!AttunedCombat.isChargedDirectMelee(attacker, defender, source, PYRESWORN_CHARGED_SWING_THRESHOLD)) {
			return;
		}
		if (!CombatTargets.isHostileOrPvpOpponent(defender, attacker)) {
			return;
		}
		// Friendly-fire and PvP guards: never ignite the attacker's own pets or
		// any villager, and only ignite another player when the world's PvP
		// game rule allows it. AFTER_DAMAGE is server-side, so the level here
		// is always a ServerLevel.
		if (defender instanceof Player targetPlayer) {
			if (!CombatTargets.canAffectPlayer(attacker, targetPlayer)) {
				return;
			}
		} else if (defender instanceof TamableAnimal pet) {
			UUID ownerId = pet.getOwnerUUID();
			if (ownerId != null && attacker.getUUID().equals(ownerId)) {
				return;
			}
		} else if (defender instanceof AbstractVillager) {
			return;
		}
		defender.setSecondsOnFire(PactTier4.pyreswornIgniteSeconds(attacker));
		markPyreswornFire(attacker, defender);
		if (attacker instanceof ServerPlayer serverPlayer) {
			PactTrials.onPyreswornIgnite(serverPlayer);
		}
	}

	/**
	 * Tidesworn's control-on-strike: a brief Slowness I that drags the foe in the
	 * current. Like Pyresworn's burn it fires on a direct melee hit and shares the
	 * same friendly-fire/PvP guards, but lands on any direct hit (no charge gate)
	 * because Slowness I is a far milder pressure than open flame.
	 */
	private static void tideswornSlow(Player attacker, LivingEntity defender, DamageSource source) {
		if (!isDirectMelee(attacker, source) || !canStrikePactTarget(attacker, defender)) {
			return;
		}
		defender.addEffect(new MobEffectInstance(
			MobEffects.MOVEMENT_SLOWDOWN, PactTier4.tideswornSlowTicks(attacker), 0, true, false, true));
		if (attacker instanceof ServerPlayer serverPlayer) {
			PactTrials.onTideswornSlow(serverPlayer);
		}
	}

	/**
	 * Forgebound's ember-on-strike: a milder Pyresworn burn — a shorter ignite on
	 * any direct melee hit, with no charge gate. Shares the direct-melee and
	 * friendly-fire/PvP guards so it never catches the attacker's own pets, a
	 * villager, or a player the world's PvP rule protects.
	 */
	private static void forgeboundSear(Player attacker, LivingEntity defender, DamageSource source) {
		if (!isDirectMelee(attacker, source) || !canStrikePactTarget(attacker, defender)) {
			return;
		}
		defender.setSecondsOnFire(PactTier4.forgeboundIgniteSeconds(attacker));
		if (attacker instanceof ServerPlayer serverPlayer) {
			PactTrials.onForgeboundIgnite(serverPlayer);
		}
	}

	/** Direct, non-projectile, non-explosion melee from this attacker. */
	private static boolean isDirectMelee(Player attacker, DamageSource source) {
		// Only on direct melee — projectile and indirect sources have a non-null direct entity.
		if (source.getDirectEntity() != attacker) {
			return false;
		}
		// Defense in depth: even if a modded weapon sets the player as the direct entity
		// for a projectile or explosion, the damage type tag still flags it correctly.
		return !source.isProjectile()
			&& !source.isExplosion();
	}

	/**
	 * Shared friendly-fire and PvP guard for the on-strike pact procs (Pyresworn,
	 * Tidesworn, Forgebound): the defender must be a hostile or a valid PvP
	 * opponent, never the attacker's own pet and never a villager. AFTER_DAMAGE is
	 * server-side, so PvP is gated through {@link CombatTargets#canAffectPlayer}.
	 */
	private static boolean canStrikePactTarget(Player attacker, LivingEntity defender) {
		if (!CombatTargets.isHostileOrPvpOpponent(defender, attacker)) {
			return false;
		}
		if (defender instanceof Player targetPlayer) {
			return CombatTargets.canAffectPlayer(attacker, targetPlayer);
		}
		if (defender instanceof TamableAnimal pet) {
			UUID ownerId = pet.getOwnerUUID();
			return ownerId == null || !attacker.getUUID().equals(ownerId);
		}
		return !(defender instanceof AbstractVillager);
	}

	/** Whether a player stands in light dim enough for Nightsworn's shelter to hold. */
	private static boolean isInDark(Player player) {
		return player.getLevel().getMaxLocalRawBrightness(player.blockPosition()) <= NIGHTSWORN_MAX_LIGHT;
	}

	private static void markPyreswornFire(Player attacker, LivingEntity defender) {
		if (!(attacker instanceof ServerPlayer serverPlayer) || !isHostile(defender)) {
			return;
		}
		pyreswornFireMarks.put(defender.getUUID(),
			new PyreswornFireMark(serverPlayer.getUUID(), ticks + PYRESWORN_CHALLENGE_WINDOW_TICKS));
	}

	/**
	 * Untethered visible kicker: a small dust burst in the defender's affinity
	 * colour at chest height, so the matchup-agnostic amplifier reads on screen.
	 */
	private static void untetheredImpactSparkle(LivingEntity defender) {
		Optional<Integer> color = affinityColor(defender);
		if (color.isEmpty() || !(defender.getLevel() instanceof ServerLevel level)) {
			return;
		}
		// DustParticleOptions takes an opaque RGB, not an ARGB — strip the alpha byte.
		level.sendParticles(
			ParticleCompat.dust(color.get(), 0.9F),
			defender.getX(),
			defender.getY() + defender.getBbHeight() * 0.6,
			defender.getZ(),
			3, 0.25, 0.25, 0.25, 0.0
		);
	}

	private static boolean canAffectCombatTarget(Player attacker, LivingEntity defender) {
		return !(defender instanceof Player targetPlayer)
			|| CombatTargets.canAffectPlayer(attacker, targetPlayer);
	}

	private static boolean hasAffinityPressure(LivingEntity defender) {
		return CombatTargets.affinityOf(defender).isPresent()
			|| (defender instanceof Player player && Attunement.isDiscord(player));
	}

	private static Optional<Integer> affinityColor(LivingEntity defender) {
		if (defender instanceof Player player && Attunement.isDiscord(player)) {
			return Optional.of(AffinityColors.DISCORD_RGB);
		}
		return CombatTargets.affinityOf(defender).map(affinity -> affinity.argb() & 0x00FFFFFF);
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		maybeAwardPyreswornChallenge(entity, source);
		maybeAwardUntetheredChallenge(entity, source);
		pyreswornFireMarks.remove(entity.getUUID());
	}

	private static void maybeAwardPyreswornChallenge(LivingEntity entity, DamageSource source) {
		PyreswornFireMark mark = pyreswornFireMarks.get(entity.getUUID());
		if (mark == null || mark.expiresAt() < ticks || !isHostile(entity)
				|| !(entity.getLevel() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity killer = AttunedCombat.attackerOf(source);
		if (killer != null && !mark.playerId().equals(killer.getUUID())) {
			return;
		}
		if (killer == null && !source.isFire()) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(mark.playerId());
		if (player != null && isAt(player, Pact.PYRESWORN)) {
			AttunedAdvancements.award(player, PYRESWORN_CHALLENGE);
		}
	}

	private static void maybeAwardStoneheartChallenge(LivingEntity defender,
			LivingEntity attacker, float dealtDamage) {
		if (!(defender instanceof ServerPlayer player) || attacker == null || attacker == defender
				|| !defender.isAlive() || dealtDamage < STONEHEART_CHALLENGE_DAMAGE) {
			return;
		}
		if (isAt(player, Pact.STONEHEART)) {
			AttunedAdvancements.award(player, STONEHEART_CHALLENGE);
		}
	}

	private static void maybeAwardUntetheredChallenge(LivingEntity entity, DamageSource source) {
		LivingEntity killer = AttunedCombat.attackerOf(source);
		if (!(killer instanceof ServerPlayer player) || entity == player || entity instanceof Player
				|| MobAffinities.of(entity).isEmpty() || !isHostile(entity)) {
			return;
		}
		if (isAt(player, Pact.UNTETHERED)) {
			AttunedAdvancements.award(player, UNTETHERED_CHALLENGE);
			if (Resonance.atApex(player)) {
				PactTrials.onUntetheredKill(player);
			}
		}
	}

	private static boolean isHostile(LivingEntity entity) {
		return entity.getType().getCategory() == MobCategory.MONSTER;
	}

	/** Per-tick: announce transitions, paint aura particles, sustain Windrunner. */
	private static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % 40 == 0) {
			pruneExpiredPyreswornMarks();
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Pact now = activeOf(player).orElse(null);
			Pact was = pactState.get(player.getUUID());
			if (now != was) {
				if (was != null && now != null) {
					// Pact-to-pact transition: narrate both the loss and the gain so a
					// player who reshuffles their Foci hears the old pact fade before
					// the new one wakes, rather than the old message being swallowed.
					pactState.put(player.getUUID(), now);
					announceLost(player, was, now);
					announceGained(player, now);
					maybeFanfare(player, now);
					// Windrunner's step-height modifier follows the affinity: pull
					// it off when leaving Windrunner, put it on when entering.
					if (was == Pact.WINDRUNNER && now != Pact.WINDRUNNER) {
						removeWindrunnerStepHeight(player);
					}
					if (was == Pact.STONEHEART) {
						PactTier4.removeStoneheartToughness(player);
					}
					if (now == Pact.WINDRUNNER) {
						applyWindrunnerStepHeight(player);
					}
					if (now == Pact.STONEHEART) {
						PactTier4.reconcileStoneheartToughness(player, now);
					}
				} else if (now != null) {
					pactState.put(player.getUUID(), now);
					announceGained(player, now);
					maybeFanfare(player, now);
					if (now == Pact.WINDRUNNER) {
						applyWindrunnerStepHeight(player);
					}
					if (now == Pact.STONEHEART) {
						PactTier4.reconcileStoneheartToughness(player, now);
					}
				} else {
					pactState.remove(player.getUUID());
					announceLost(player, was, null);
					if (was == Pact.WINDRUNNER) {
						removeWindrunnerStepHeight(player);
					}
					if (was == Pact.STONEHEART) {
						PactTier4.removeStoneheartToughness(player);
					}
				}
			}
			if (now != null) {
				if (ticks % AURA_TICK == 0) {
					paintAura(player, now);
				}
				if (now == Pact.WINDRUNNER && ticks % WINDRUNNER_TICK == 0) {
					// SPEED refreshed every WINDRUNNER_TICK ticks. Tier 4 grants Speed II while sprinting.
					player.addEffect(new MobEffectInstance(
						MobEffects.MOVEMENT_SPEED, WINDRUNNER_TICK * 4, PactTier4.windrunnerSpeedAmplifier(player),
						true, false, true));
				}
				if (now == Pact.WILDROOT && ticks % WILDROOT_REGEN_TICK == 0) {
					// Regeneration refreshed every WILDROOT_REGEN_TICK ticks — Tier 4 upgrades to Regen II.
					player.addEffect(new MobEffectInstance(
						MobEffects.REGENERATION, WILDROOT_REGEN_TICK * 2, PactTier4.wildrootRegenAmplifier(player),
						true, false, true));
				}
			}
			trackWindrunnerChallenge(player, now);
		}
	}

	private static void pruneExpiredPyreswornMarks() {
		pyreswornFireMarks.entrySet().removeIf(entry -> entry.getValue().expiresAt() < ticks);
	}

	private static void trackWindrunnerChallenge(ServerPlayer player, Pact pact) {
		UUID id = player.getUUID();
		if (pact != Pact.WINDRUNNER || !player.isSprinting()
				|| player.isPassenger() || player.isFallFlying()) {
			windrunnerRuns.remove(id);
			return;
		}
		ResourceKey<Level> dimension = player.getLevel().dimension();
		double x = player.getX();
		double z = player.getZ();
		WindrunnerRun previous = windrunnerRuns.get(id);
		if (previous == null || !previous.dimension().equals(dimension)) {
			windrunnerRuns.put(id, new WindrunnerRun(dimension, x, z, 0.0));
			return;
		}
		double dx = x - previous.x();
		double dz = z - previous.z();
		double step = Math.sqrt(dx * dx + dz * dz);
		if (step > WINDRUNNER_MAX_DELTA_PER_TICK) {
			windrunnerRuns.put(id, new WindrunnerRun(dimension, x, z, 0.0));
			return;
		}
		double distance = previous.distance() + step;
		if (distance >= WINDRUNNER_CHALLENGE_DISTANCE) {
			AttunedAdvancements.award(player, WINDRUNNER_CHALLENGE);
			windrunnerRuns.remove(id);
			return;
		}
		windrunnerRuns.put(id, new WindrunnerRun(dimension, x, z, distance));
	}

	/**
	 * Lifts the player's {@link Attributes#STEP_HEIGHT} by {@link #WINDRUNNER_STEP_BONUS}
	 * so they can walk up a full-block rise. Idempotent — a player who already
	 * carries the modifier is left untouched.
	 */
	private static void applyWindrunnerStepHeight(ServerPlayer player) {
		// Minecraft 1.19.4 has no step-height attribute; Windrunner keeps its
		// speed/aura/challenge behavior on this branch.
	}

	/** Drops the Windrunner step-height modifier if present. */
	private static void removeWindrunnerStepHeight(ServerPlayer player) {
		// No step-height attribute exists to clean up before Minecraft 1.20.5.
	}

	private static void paintAura(ServerPlayer player, Pact pact) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + 0.05;
		double z = player.getZ();
		ParticleOptions particle = auraParticle(pact);
		// Affinity-pact auras swell with the player's Resonance: a quiet wisp at
		// rest, a tall column at Apex. Untethered has no affinity-resonance tie,
		// so its dust column stays at the baseline count.
		int count;
		double ySpread;
		if (pact == Pact.UNTETHERED) {
			count = 3;
			ySpread = 0.05;
		} else {
			float resonance = Resonance.get(player);
			count = 1 + Math.round(4 * resonance);
			ySpread = 0.05 + 0.45 * resonance;
		}
		level.sendParticles(particle, x, y, z, count, 0.3, ySpread, 0.3, 0.005);
	}

	private static ParticleOptions auraParticle(Pact pact) {
		return switch (pact) {
			case PYRESWORN -> ParticleTypes.SMALL_FLAME;
			case STONEHEART -> ParticleCompat.dust(0xC8A05A, 0.8F);
			case WINDRUNNER -> ParticleTypes.CLOUD;
			case RADIANT_COVENANT -> ParticleCompat.dust(Affinity.HOLY.argb() & 0x00FFFFFF, 0.9F);
			// The promoted single-affinity pacts mirror Radiant Covenant's shape: a
			// modest dust wisp tinted by the bound affinity's colour.
			case TIDESWORN -> ParticleCompat.dust(Affinity.TIDE.argb() & 0x00FFFFFF, 0.9F);
			case FORGEBOUND -> ParticleCompat.dust(Affinity.FORGE.argb() & 0x00FFFFFF, 0.9F);
			case WILDROOT -> ParticleCompat.dust(Affinity.VERDANT.argb() & 0x00FFFFFF, 0.9F);
			case NIGHTSWORN -> ParticleCompat.dust(Affinity.UMBRAL.argb() & 0x00FFFFFF, 0.9F);
			case UNTETHERED -> ParticleCompat.dust(AffinityColors.DISCORD_RGB, 0.9F);
		};
	}

	private static void announceGained(ServerPlayer player, Pact pact) {
		playPactSound(player, pact, true);
		PlayerMessages.system(player, Component.translatable("pact.attuned.awakened")
			.withStyle(ChatFormatting.GRAY)
			.append(pact.displayName().withStyle(pact.chatColor(), ChatFormatting.BOLD))
			.append(Component.literal(". ").withStyle(ChatFormatting.GRAY))
			.append(pact.description().withStyle(ChatFormatting.GRAY)));
		AttunedAdvancements.award(player, "attunement/pact_" + pact.name().toLowerCase(Locale.ROOT));
	}

	private static void announceLost(ServerPlayer player, Pact pact, Pact replacement) {
		if (pact != null) {
			playPactSound(player, pact, false);
		}
		Component name = pact == null
			? Component.translatable("pact.attuned.fades.generic")
			: pact.displayName().withStyle(pact.chatColor(), ChatFormatting.BOLD);
		PlayerMessages.system(player, fadeMessage(player, pact, replacement, name).copy()
			.withStyle(ChatFormatting.GRAY));
	}

	private static Component fadeMessage(ServerPlayer player, Pact pact, Pact replacement, Component name) {
		if (replacement != null) {
			return Component.translatable("pact.attuned.fades.replaced", name,
				replacement.displayName().withStyle(replacement.chatColor(), ChatFormatting.BOLD));
		}
		if (pact == null) {
			return Component.translatable("pact.attuned.fades", name);
		}
		EnumMap<Affinity, Integer> counts = activeAffinityCounts(player);
		if (counts.isEmpty()) {
			return Component.translatable("pact.attuned.fades.empty", name);
		}
		if (pact == Pact.UNTETHERED) {
			return !isManifoldSpread(counts)
				? Component.translatable("pact.attuned.fades.affinities", name)
				: Component.translatable("pact.attuned.fades", name);
		}
		if (counts.size() >= 2) {
			return Component.translatable("pact.attuned.fades.discord", name);
		}
		Affinity affinity = pact.affinity().orElse(null);
		if (affinity != null && counts.getOrDefault(affinity, 0) < SINGLE_AFFINITY_THRESHOLD) {
			return Component.translatable("pact.attuned.fades.short", name, affinityName(affinity));
		}
		return Component.translatable("pact.attuned.fades", name);
	}

	private static void playPactSound(ServerPlayer player, Pact pact, boolean awakening) {
		ServerLevel level = (ServerLevel) player.getLevel();
		PactSound sound = awakening ? awakenSound(pact) : fadeSound(pact);
		level.playSound(null, player.blockPosition(), sound.event(),
			SoundSource.PLAYERS, sound.volume(), sound.pitch());
	}

	private static PactSound awakenSound(Pact pact) {
		return switch (pact) {
			case PYRESWORN -> new PactSound(SoundEvents.FLINTANDSTEEL_USE, 0.45F, 1.25F);
			case STONEHEART -> new PactSound(SoundEvents.TUFF_PLACE, 0.45F, 0.85F);
			case WINDRUNNER -> new PactSound(SoundEvents.PHANTOM_FLAP, 0.35F, 1.55F);
			case RADIANT_COVENANT -> new PactSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45F, 1.45F);
			case TIDESWORN -> new PactSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.55F, 1.2F);
			case FORGEBOUND -> new PactSound(SoundEvents.ANVIL_LAND, 0.35F, 1.35F);
			case WILDROOT -> new PactSound(SoundEvents.GRASS_PLACE, 0.55F, 1.1F);
			case NIGHTSWORN -> new PactSound(SoundEvents.SCULK_BLOCK_CHARGE, 0.45F, 1.1F);
			case UNTETHERED -> new PactSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.45F, 1.15F);
		};
	}

	private static PactSound fadeSound(Pact pact) {
		return switch (pact) {
			case PYRESWORN -> new PactSound(SoundEvents.FIRE_EXTINGUISH, 0.35F, 1.35F);
			case STONEHEART -> new PactSound(SoundEvents.TUFF_HIT, 0.4F, 0.7F);
			case WINDRUNNER -> new PactSound(SoundEvents.WOOL_STEP, 0.35F, 1.6F);
			case RADIANT_COVENANT -> new PactSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 0.9F);
			case TIDESWORN -> new PactSound(SoundEvents.AMBIENT_UNDERWATER_EXIT, 0.4F, 0.9F);
			case FORGEBOUND -> new PactSound(SoundEvents.ANVIL_HIT, 0.35F, 0.8F);
			case WILDROOT -> new PactSound(SoundEvents.GRASS_HIT, 0.4F, 0.85F);
			case NIGHTSWORN -> new PactSound(SoundEvents.SCULK_BLOCK_HIT, 0.4F, 0.8F);
			case UNTETHERED -> new PactSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.4F, 0.8F);
		};
	}

	private record PactSound(SoundEvent event, float volume, float pitch) {}
	private record PyreswornFireMark(UUID playerId, int expiresAt) {}
	private record WindrunnerRun(ResourceKey<Level> dimension, double x, double z, double distance) {}

	/**
	 * If this is the first time the player has woken {@code pact} on this
	 * character, fires a one-time celebration on top of the normal announcement
	 * and records the milestone so it never repeats. Uses the shared ONBOARDING
	 * attachment so the marker persists across death and respawn.
	 */
	private static void maybeFanfare(ServerPlayer player, Pact pact) {
		String onboardId = "pact_first_" + pact.name().toLowerCase(Locale.ROOT);
		if (AttunedAttachments.sawOnboarding(player, onboardId)) {
			return;
		}
		AttunedAttachments.markOnboarding(player, onboardId);
		fanfare(player, pact);
	}

	/**
	 * The first-pact celebration: a longer chime layered over a beacon-power
	 * select, a sparkle burst around the player and a single styled chat line
	 * announcing the milestone in the pact's colour.
	 */
	private static void fanfare(ServerPlayer player, Pact pact) {
		ServerLevel level = (ServerLevel) player.getLevel();
		level.playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 0.8F);
		level.playSound(null, player.blockPosition(),
			SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.2F);
		level.sendParticles(
			ParticleTypes.ENCHANT,
			player.getX(),
			player.getY() + player.getBbHeight() * 0.5,
			player.getZ(),
			20, 0.6, 0.8, 0.6, 0.5
		);
		PlayerMessages.system(player, Component.translatable("pact.attuned.first_pact")
			.withStyle(pact.chatColor(), ChatFormatting.BOLD, ChatFormatting.ITALIC));
		AttunedAdvancements.award(player, "attunement/first_pact");
	}

	static int pyreswornIgniteSecondsBase() { return PYRESWORN_IGNITE_SECONDS; }
	static int forgeboundIgniteSecondsBase() { return FORGEBOUND_IGNITE_SECONDS; }
	static int tideswornSlowTicksBase() { return TIDESWORN_SLOW_TICKS; }
	static float radiantUndeadBonusBase() { return RADIANT_COVENANT_UNDEAD_BONUS; }
	static float nightswornDarkDampenBase() { return NIGHTSWORN_DARK_DAMPEN; }
}
