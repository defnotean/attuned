package dev.attuned.pacts;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.MobAffinities;
import dev.attuned.combat.Resonance;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The Pacts system: detection, activation announcement, per-tick effects and
 * damage hooks for the named set bonuses ({@link Pact}).
 *
 * <p>A player has at most one pact active at any moment:
 * <ul>
 *   <li><b>Pyresworn</b> — three or more active Fury Foci and no other affinity.</li>
 *   <li><b>Stoneheart</b> — three or more active Bastion Foci and no other affinity.</li>
 *   <li><b>Windrunner</b> — three or more active Zephyr Foci and no other affinity.</li>
 *   <li><b>Untethered</b> — at least one active Focus of every affinity, the
 *       Manifold path. Beats any single-affinity threshold when both qualify.</li>
 * </ul>
 *
 * <p>Per-tick aura particles, a constant low-tier MobEffect for Windrunner, an
 * outgoing-damage hook for Untethered, an incoming-damage hook for Stoneheart
 * and an AFTER_DAMAGE hook for Pyresworn all branch on the result of
 * {@link #activeOf}.</p>
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
	/** Untethered requires at least one Focus of each of the three affinities. */
	private static final int UNTETHERED_AFFINITY_COUNT = 3;

	/**
	 * Permanent {@link Attributes#STEP_HEIGHT} modifier id used by Windrunner.
	 * Adds {@link #WINDRUNNER_STEP_BONUS} to the vanilla 0.6 baseline, letting a
	 * Windrunner step a full block — the tops of stairs, slabs and single-block
	 * rises — without ever leaving the ground.
	 */
	private static final Identifier WINDRUNNER_STEP_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("attuned", "windrunner_step");
	/** Step-height bonus added on top of the vanilla 0.6 baseline (0.6 + 0.5 = 1.1). */
	private static final double WINDRUNNER_STEP_BONUS = 0.5;

	/** Per-player pact as of last tick, for spotting on/off transitions. */
	private static final Map<UUID, Pact> pactState = new HashMap<>();
	/** Per-server tick counter — drives aura cadence and the Windrunner effect. */
	private static int ticks;

	/** Registers tick handlers, damage hooks and the cleanup callback. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Pacts::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Pacts::afterDamage);
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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ticks = 0;
			pactState.clear();
		});
		AttunedPlayerCleanup.onForget(pactState::remove);
	}

	/** The pact a player has woken, if any. */
	public static Optional<Pact> activeOf(Player player) {
		EnumMap<Affinity, Integer> counts = activeAffinityCounts(player);
		if (counts.size() >= UNTETHERED_AFFINITY_COUNT) {
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
	 * Short UI hint for a build that is exactly one same-affinity active Focus
	 * away from a single-affinity pact.
	 */
	public static Optional<Component> previewOf(Player player) {
		if (activeOf(player).isPresent() || Attunement.isDiscord(player)) {
			return Optional.empty();
		}
		EnumMap<Affinity, Integer> counts = activeAffinityCounts(player);
		if (counts.size() != 1) {
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
		Pact pact = Pact.ofAffinity(affinity);
		return Optional.of(pact.displayName().withStyle(pact.chatColor(), ChatFormatting.BOLD)
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
		var registry = player.level().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
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
		// Stoneheart: a defender-side dampen on everything, no matchup gating.
		if (defender instanceof Player defenderPlayer && isAt(defenderPlayer, Pact.STONEHEART)) {
			amount *= (1.0F - STONEHEART_DAMPEN);
		}
		// Untethered: an attacker-side amplifier against any affinity-bearing mob.
		if (source.getEntity() instanceof Player attackerPlayer
				&& isAt(attackerPlayer, Pact.UNTETHERED)
				&& !(defender instanceof Player)
				&& MobAffinities.of(defender).isPresent()) {
			amount *= (1.0F + UNTETHERED_AMPLIFY);
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
		if (dealtDamage <= 0.0F) {
			return;
		}
		if (!(source.getEntity() instanceof Player attacker)) {
			return;
		}
		if (attacker == defender || !defender.isAlive()) {
			return;
		}
		if (isAt(attacker, Pact.PYRESWORN)) {
			pyreswornIgnite(attacker, defender, source);
		}
		if (isAt(attacker, Pact.UNTETHERED) && !(defender instanceof Player)) {
			untetheredImpactSparkle(defender);
		}
	}

	/** Pyresworn's fire-on-strike, gated to direct melee and to at-least-half-charged swings. */
	private static void pyreswornIgnite(Player attacker, LivingEntity defender, DamageSource source) {
		// Only on direct melee — projectile and indirect sources have a non-null direct entity.
		if (source.getDirectEntity() != attacker) {
			return;
		}
		// Defense in depth: even if a modded weapon sets the player as the direct entity
		// for a projectile or explosion, the damage type tag still flags it correctly.
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			return;
		}
		// Only on at-least-half-charged swings — discourages the most extreme spam-clicking.
		if (attacker.getAttackStrengthScale(0.5F) < PYRESWORN_CHARGED_SWING_THRESHOLD) {
			return;
		}
		// Friendly-fire and PvP guards: never ignite the attacker's own pets or
		// any villager, and only ignite another player when the world's PvP
		// game rule allows it. AFTER_DAMAGE is server-side, so the level here
		// is always a ServerLevel.
		if (defender instanceof Player) {
			if (defender.level() instanceof ServerLevel serverLevel
					&& !serverLevel.getGameRules().get(GameRules.PVP)) {
				return;
			}
		} else if (defender instanceof TamableAnimal pet) {
			var ownerRef = pet.getOwnerReference();
			if (ownerRef != null && attacker.getUUID().equals(ownerRef.getUUID())) {
				return;
			}
		} else if (defender instanceof AbstractVillager) {
			return;
		}
		defender.igniteForSeconds(PYRESWORN_IGNITE_SECONDS);
	}

	/**
	 * Untethered visible kicker: a small dust burst in the defender's affinity
	 * colour at chest height, so the matchup-agnostic amplifier reads on screen.
	 */
	private static void untetheredImpactSparkle(LivingEntity defender) {
		Optional<Affinity> defAffinity = MobAffinities.of(defender);
		if (defAffinity.isEmpty() || !(defender.level() instanceof ServerLevel level)) {
			return;
		}
		// DustParticleOptions takes an opaque RGB, not an ARGB — strip the alpha byte.
		int rgb = defAffinity.get().argb() & 0x00FFFFFF;
		level.sendParticles(
			new DustParticleOptions(rgb, 0.9F),
			defender.getX(),
			defender.getY() + defender.getBbHeight() * 0.6,
			defender.getZ(),
			3, 0.25, 0.25, 0.25, 0.0
		);
	}

	/** Per-tick: announce transitions, paint aura particles, sustain Windrunner. */
	private static void tick(MinecraftServer server) {
		ticks++;
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
					if (now == Pact.WINDRUNNER) {
						applyWindrunnerStepHeight(player);
					}
				} else if (now != null) {
					pactState.put(player.getUUID(), now);
					announceGained(player, now);
					maybeFanfare(player, now);
					if (now == Pact.WINDRUNNER) {
						applyWindrunnerStepHeight(player);
					}
				} else {
					pactState.remove(player.getUUID());
					announceLost(player, was, null);
					if (was == Pact.WINDRUNNER) {
						removeWindrunnerStepHeight(player);
					}
				}
			}
			if (now != null) {
				if (ticks % AURA_TICK == 0) {
					paintAura(player, now);
				}
				if (now == Pact.WINDRUNNER && ticks % WINDRUNNER_TICK == 0) {
					// SPEED 0 (Speed I) refreshed every WINDRUNNER_TICK ticks. Hidden particles,
					// shown in HUD so the player knows the pact is sustaining the buff.
					player.addEffect(new MobEffectInstance(
						MobEffects.SPEED, WINDRUNNER_TICK * 4, 0, true, false, true));
				}
			}
		}
	}

	/**
	 * Lifts the player's {@link Attributes#STEP_HEIGHT} by {@link #WINDRUNNER_STEP_BONUS}
	 * so they can walk up a full-block rise. Idempotent — a player who already
	 * carries the modifier is left untouched.
	 */
	private static void applyWindrunnerStepHeight(ServerPlayer player) {
		AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
		if (attr == null) {
			return;
		}
		if (attr.getModifier(WINDRUNNER_STEP_MODIFIER_ID) != null) {
			return;
		}
		attr.addPermanentModifier(new AttributeModifier(
			WINDRUNNER_STEP_MODIFIER_ID,
			WINDRUNNER_STEP_BONUS,
			AttributeModifier.Operation.ADD_VALUE));
	}

	/** Drops the Windrunner step-height modifier if present. */
	private static void removeWindrunnerStepHeight(ServerPlayer player) {
		AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
		if (attr == null) {
			return;
		}
		attr.removeModifier(WINDRUNNER_STEP_MODIFIER_ID);
	}

	private static void paintAura(ServerPlayer player, Pact pact) {
		ServerLevel level = (ServerLevel) player.level();
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
			case STONEHEART -> new DustParticleOptions(0xC8A05A, 0.8F);
			case WINDRUNNER -> ParticleTypes.CLOUD;
			case UNTETHERED -> new DustParticleOptions(AffinityColors.DISCORD_RGB, 0.9F);
		};
	}

	private static void announceGained(ServerPlayer player, Pact pact) {
		playPactSound(player, pact, true);
		player.sendSystemMessage(Component.translatable("pact.attuned.awakened")
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
		player.sendSystemMessage(fadeMessage(player, pact, replacement, name).copy()
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
			return counts.size() < UNTETHERED_AFFINITY_COUNT
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
		ServerLevel level = (ServerLevel) player.level();
		PactSound sound = awakening ? awakenSound(pact) : fadeSound(pact);
		level.playSound(null, player.blockPosition(), sound.event(),
			SoundSource.PLAYERS, sound.volume(), sound.pitch());
	}

	private static PactSound awakenSound(Pact pact) {
		return switch (pact) {
			case PYRESWORN -> new PactSound(SoundEvents.FLINTANDSTEEL_USE, 0.45F, 1.25F);
			case STONEHEART -> new PactSound(SoundEvents.TUFF_PLACE, 0.45F, 0.85F);
			case WINDRUNNER -> new PactSound(SoundEvents.WIND_CHARGE_THROW, 0.35F, 1.55F);
			case UNTETHERED -> new PactSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.45F, 1.15F);
		};
	}

	private static PactSound fadeSound(Pact pact) {
		return switch (pact) {
			case PYRESWORN -> new PactSound(SoundEvents.FIRE_EXTINGUISH, 0.35F, 1.35F);
			case STONEHEART -> new PactSound(SoundEvents.TUFF_HIT, 0.4F, 0.7F);
			case WINDRUNNER -> new PactSound(SoundEvents.WOOL_STEP, 0.35F, 1.6F);
			case UNTETHERED -> new PactSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.4F, 0.8F);
		};
	}

	private record PactSound(SoundEvent event, float volume, float pitch) {}

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
		ServerLevel level = (ServerLevel) player.level();
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
		player.sendSystemMessage(Component.translatable("pact.attuned.first_pact")
			.withStyle(pact.chatColor(), ChatFormatting.BOLD, ChatFormatting.ITALIC));
		AttunedAdvancements.award(player, "attunement/first_pact");
	}
}
