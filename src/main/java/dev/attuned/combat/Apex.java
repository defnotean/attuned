package dev.attuned.combat;

import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.network.ActionBarMessages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Apex capstones: late-build passives gated by Focus layout, nearly full
 * attunement capacity, and combat Resonance.
 */
public final class Apex {
	private Apex() {}

	public enum Capstone {
		EXECUTE("Execute", "Your strikes finish off low-health foes.", Affinity.FURY,
			ChatFormatting.RED, Affinity.FURY.argb()),
		UNYIELDING("Unyielding", "No single blow can land hard, and knockback is ignored.", Affinity.BASTION,
			ChatFormatting.GOLD, Affinity.BASTION.argb()),
		UNTOUCHABLE("Untouchable", "A chance to dodge attacks outright while sprinting.", Affinity.ZEPHYR,
			ChatFormatting.AQUA, Affinity.ZEPHYR.argb()),
		JUDGMENT("Judgment", "Judgment marks wounded Fury-aligned foes for a decisive strike.", Affinity.HOLY,
			ChatFormatting.YELLOW, Affinity.HOLY.argb()),
		RIPTIDE("Riptide", "Your apex strikes drag foes into the current.", Affinity.TIDE,
			ChatFormatting.BLUE, Affinity.TIDE.argb()),
		CRUCIBLE("Crucible", "Your apex strikes sear foes with forge-heat.", Affinity.FORGE,
			ChatFormatting.DARK_RED, Affinity.FORGE.argb()),
		BLOOMWARD("Bloomward", "Your apex strikes return life to you.", Affinity.VERDANT,
			ChatFormatting.GREEN, Affinity.VERDANT.argb()),
		GLOAMING("Gloaming", "Your apex strikes sap a foe's strength.", Affinity.UMBRAL,
			ChatFormatting.DARK_PURPLE, Affinity.UMBRAL.argb()),
		MAELSTROM("Maelstrom", "Discord Apex adds force to direct hits and scrambles struck foes.", null,
			ChatFormatting.LIGHT_PURPLE, AffinityColors.DISCORD_ARGB),
		STILLPOINT("Stillpoint", "Neutral Apex grants Absorption pulses and denies affinity pressure.", null,
			ChatFormatting.GRAY, AffinityColors.NEUTRAL_ARGB);

		private final String displayName;
		private final String description;
		private final Affinity affinity;
		private final ChatFormatting chatColor;
		private final int argb;

		Capstone(String displayName, String description, Affinity affinity, ChatFormatting chatColor, int argb) {
			this.displayName = displayName;
			this.description = description;
			this.affinity = affinity;
			this.chatColor = chatColor;
			this.argb = argb;
		}

		public String displayName() {
			return displayName;
		}

		public String description() {
			return description;
		}

		public Optional<Affinity> affinity() {
			return Optional.ofNullable(affinity);
		}

		public ChatFormatting chatColor() {
			return chatColor;
		}

		public int argb() {
			return argb;
		}

		/**
		 * The capstone a single committed affinity resolves to. Every affinity now
		 * owns a capstone: the original four (Fury, Bastion, Zephyr, Holy) plus the
		 * promoted four (Tide, Forge, Verdant, Umbral), so a build fully committed to
		 * any single affinity resolves to a present capstone.
		 */
		public static Optional<Capstone> ofAffinity(Affinity affinity) {
			return switch (affinity) {
				case FURY -> Optional.of(EXECUTE);
				case BASTION -> Optional.of(UNYIELDING);
				case ZEPHYR -> Optional.of(UNTOUCHABLE);
				case HOLY -> Optional.of(JUDGMENT);
				case TIDE -> Optional.of(RIPTIDE);
				case FORGE -> Optional.of(CRUCIBLE);
				case VERDANT -> Optional.of(BLOOMWARD);
				case UMBRAL -> Optional.of(GLOAMING);
			};
		}
	}

	private static final float EXECUTE_DAMAGE = 100000.0F;
	private static final float EXECUTE_NORMAL = 0.20F;
	private static final float EXECUTE_EMPOWERED = 0.35F;
	private static final float CAP_NORMAL = 0.15F;
	private static final float CAP_EMPOWERED = 0.10F;
	private static final float DODGE_NORMAL = 0.40F;
	private static final float DODGE_EMPOWERED = 0.65F;
	private static final float JUDGMENT_THRESHOLD = 0.30F;
	private static final float JUDGMENT_DAMAGE_BONUS = 0.40F;
	private static final float MAELSTROM_DAMAGE_BONUS = 0.10F;
	private static final int MAELSTROM_SCRAMBLE_TICKS = 60;
	private static final int STILLPOINT_ABSORPTION_TICKS = 60;
	private static final int STILLPOINT_ABSORPTION_COOLDOWN_TICKS = 160;

	// Affinity-capstone on-hit procs (attacker-side, landed apex melee only).
	// Each is matchup-scaled: NORMAL fires the base value, EMPOWERED roughly
	// doubles it, NEUTRALIZED does not fire at all.
	private static final int RIPTIDE_SLOWNESS_TICKS = 40;
	private static final int RIPTIDE_SLOWNESS_TICKS_EMPOWERED = 80;
	private static final int CRUCIBLE_FIRE_SECONDS = 3;
	private static final int CRUCIBLE_FIRE_SECONDS_EMPOWERED = 5;
	private static final float BLOOMWARD_HEAL = 1.5F;
	private static final float BLOOMWARD_HEAL_EMPOWERED = 3.0F;
	private static final int GLOAMING_WEAKNESS_TICKS = 40;
	private static final int GLOAMING_WEAKNESS_TICKS_EMPOWERED = 80;

	// Apex identity abilities: fired from the Focus Ability key when no ability Focus is active.
	static final int MAELSTROM_NOVA_COOLDOWN_TICKS = 600;
	static final int STILLPOINT_FIELD_COOLDOWN_TICKS = 600;
	private static final double MAELSTROM_NOVA_RADIUS = 5.0D;
	private static final double STILLPOINT_FIELD_RADIUS = 7.0D;
	private static final double MAELSTROM_NOVA_KNOCKBACK = 1.4D;
	private static final int MAELSTROM_NOVA_WEAKNESS_TICKS = 100;
	private static final int STILLPOINT_FIELD_SLOWNESS_TICKS = 60;

	private static final Map<UUID, Capstone> apexState = new HashMap<>();
	private static final Map<UUID, Boolean> armedState = new HashMap<>();
	private static final Map<ScrambleKey, Long> maelstromScrambles = new HashMap<>();
	private static final Map<UUID, Long> stillpointPulses = new HashMap<>();
	private static final Map<UUID, Long> identityCooldowns = new HashMap<>();
	private static boolean initialized;

	private enum Matchup { EMPOWERED, NORMAL, NEUTRALIZED }

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Apex::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Apex::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
			maelstromScrambles.keySet().removeIf(key -> key.targetId().equals(entity.getUUID())));
		ServerTickEvents.END_SERVER_TICK.register(Apex::tick);
		AttunedServerCleanup.onStop(() -> {
			apexState.clear();
			armedState.clear();
			maelstromScrambles.clear();
			stillpointPulses.clear();
			identityCooldowns.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			apexState.remove(uuid);
			armedState.remove(uuid);
			stillpointPulses.remove(uuid);
			// identityCooldowns is deliberately kept across disconnect so a
			// relog cannot reset the Maelstrom/Stillpoint ability cooldown;
			// entries expire naturally when the cooldown elapses.
			maelstromScrambles.keySet().removeIf(key ->
				key.playerId().equals(uuid) || key.targetId().equals(uuid));
		});
	}

	public static Optional<Capstone> resolveCapstone(List<Optional<Affinity>> activeAffinities,
			int used, int capacity) {
		return ApexCapstoneResolver.resolve(activeAffinities, used, capacity);
	}

	public static Optional<Capstone> capstoneOf(Player player) {
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		List<Integer> active = resolution.activeSlots();
		List<Optional<Affinity>> activeAffinities = new ArrayList<>(active.size());
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int used = 0;
		for (int slot : active) {
			Optional<FocusDefinition> maybeDefinition = Attunement.definitionFor(player, inv.get(slot));
			if (maybeDefinition.isEmpty()) {
				activeAffinities.add(Optional.empty());
				continue;
			}
			FocusDefinition definition = maybeDefinition.get();
			activeAffinities.add(definition.affinity());
			used += Attunement.effectiveCost(definition, inv.get(slot));
		}
		return resolveCapstone(activeAffinities, used, Attunement.capacity(player));
	}

	public static Optional<Affinity> affinityOf(Player player) {
		return capstoneOf(player).flatMap(Capstone::affinity);
	}

	public static boolean isAt(Player player, Affinity affinity) {
		return affinityOf(player).filter(a -> a == affinity).isPresent();
	}

	public static boolean isAt(Player player, Capstone capstone) {
		return capstoneOf(player).filter(c -> c == capstone).isPresent();
	}

	/**
	 * Player-facing capstone name for a committed affinity. Every affinity now owns
	 * a capstone, so this always resolves to a name.
	 */
	public static String capstoneName(Affinity affinity) {
		return Capstone.ofAffinity(affinity).map(Capstone::displayName).orElse("");
	}

	/**
	 * Player-facing capstone description for a committed affinity. Every affinity
	 * now owns a capstone, so this always resolves to a description.
	 */
	public static String capstoneDescription(Affinity affinity) {
		return Capstone.ofAffinity(affinity).map(Capstone::description).orElse("");
	}

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
		LivingEntity attacker = context.attacker();
		Player defenderPlayer = defender instanceof Player defenderCandidate ? defenderCandidate : null;
		Player attackerPlayer = attacker instanceof Player attackerCandidate ? attackerCandidate : null;
		Capstone defenderCapstone = defenderPlayer != null
			? context.capstoneOf(defenderPlayer).orElse(null)
			: null;
		Capstone attackerCapstone = attackerPlayer != null
			? context.capstoneOf(attackerPlayer).orElse(null)
			: null;
		boolean defenderAtApex = defenderPlayer != null && defenderCapstone != null
			&& context.atApex(defenderPlayer);
		boolean attackerAtApex = attackerPlayer != null && attackerCapstone != null
			&& context.atApex(attackerPlayer);

		if (defenderPlayer != null
				&& defenderCapstone == Capstone.UNYIELDING
				&& defenderAtApex) {
			Matchup matchup = matchupAgainst(Affinity.BASTION, attacker, context);
			if (matchup != Matchup.NEUTRALIZED) {
				float fraction = matchup == Matchup.EMPOWERED ? CAP_EMPOWERED : CAP_NORMAL;
				float cap = defenderPlayer.getMaxHealth() * fraction;
				if (amount > cap) {
					amount = DamageFormula.cap(amount, cap);
					if (defenderPlayer instanceof ServerPlayer serverPlayer) {
						CombatFeedback.unyieldingCap(serverPlayer);
					}
				}
			}
		}

		if (defender.getMaxHealth() > 0.0F
				&& attackerPlayer != null
				&& attackerCapstone == Capstone.EXECUTE
				&& attackerAtApex
				&& isApexMeleeTarget(defender, attackerPlayer, source)) {
			Matchup matchup = matchupAgainst(Affinity.FURY, defender, context);
			if (matchup != Matchup.NEUTRALIZED) {
				float threshold = matchup == Matchup.EMPOWERED ? EXECUTE_EMPOWERED : EXECUTE_NORMAL;
				if (defender.getHealth() / defender.getMaxHealth() <= threshold) {
					amount = DamageFormula.floor(amount, EXECUTE_DAMAGE);
					if (attackerPlayer instanceof ServerPlayer serverPlayer) {
						CombatFeedback.executeFinisher(serverPlayer, defender);
					}
				}
			}
		}

		if (defender.getMaxHealth() > 0.0F
				&& attackerPlayer != null
				&& attackerCapstone == Capstone.JUDGMENT
				&& attackerAtApex
				&& isApexMeleeTarget(defender, attackerPlayer, source)) {
			Matchup matchup = matchupAgainst(Affinity.HOLY, defender, context);
			if (matchup == Matchup.EMPOWERED
					&& defender.getHealth() / defender.getMaxHealth() <= JUDGMENT_THRESHOLD) {
				amount = DamageFormula.amplify(amount, JUDGMENT_DAMAGE_BONUS);
				if (attackerPlayer instanceof ServerPlayer serverPlayer) {
					CombatFeedback.judgmentStrike(serverPlayer, defender);
				}
			}
		}

		if (defender.getMaxHealth() > 0.0F
				&& attackerPlayer != null
				&& attackerCapstone == Capstone.MAELSTROM
				&& attackerAtApex
				&& isApexMeleeTarget(defender, attackerPlayer, source)
				&& context.hasAffinityPressure(defender)) {
			amount = DamageFormula.amplify(amount, MAELSTROM_DAMAGE_BONUS);
			markScrambled(attackerPlayer, defender);
			if (attackerPlayer instanceof ServerPlayer serverPlayer) {
				CombatFeedback.maelstromHit(serverPlayer, defender);
			}
		}
		return amount;
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || AttunedCombat.isReflecting()) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);

		// Defender-side STILLPOINT: unchanged. Only a player can hold a capstone, so
		// this still gates on the defender being a player exactly as before.
		if (defender instanceof Player defenderPlayer
				&& attacker != null
				&& isAt(defenderPlayer, Capstone.STILLPOINT)
				&& Resonance.atApex(defenderPlayer)
				&& CombatTargets.isHostileOrPvpOpponent(attacker, defenderPlayer)
				&& hasAffinityPressure(attacker)) {
			pulseStillpoint(defenderPlayer);
		}

		// Attacker-side affinity-capstone procs: fire on a landed apex melee hit
		// against hostile mobs or valid PvP opponents. Only the four promoted-affinity
		// capstones live here; the original capstones keep their existing homes.
		applyAffinityCapstoneProc(defender, source, attacker);
	}

	private static void applyAffinityCapstoneProc(LivingEntity defender, DamageSource source,
			LivingEntity attacker) {
		if (!(attacker instanceof Player attackerPlayer)) {
			return;
		}
		CombatContext context = CombatContext.of(defender, source);
		Capstone capstone = context.capstoneOf(attackerPlayer).orElse(null);
		if (capstone == null
				|| !context.atApex(attackerPlayer)
				|| !isApexMeleeTarget(defender, attackerPlayer, source)) {
			return;
		}
		switch (capstone) {
			case RIPTIDE -> procRiptide(defender, attackerPlayer, context);
			case CRUCIBLE -> procCrucible(defender, attackerPlayer, context);
			case BLOOMWARD -> procBloomward(defender, attackerPlayer, context);
			case GLOAMING -> procGloaming(defender, attackerPlayer, context);
			// EXECUTE/UNYIELDING/UNTOUCHABLE/JUDGMENT/MAELSTROM/STILLPOINT keep their
			// existing homes (adjustDamage / allowDamage / afterDamage above), so they
			// must not double-apply here.
			default -> { }
		}
	}

	private static void procRiptide(LivingEntity defender, Player attacker, CombatContext context) {
		Matchup matchup = matchupAgainst(Affinity.TIDE, defender, context);
		if (matchup == Matchup.NEUTRALIZED) {
			return;
		}
		int ticks = matchup == Matchup.EMPOWERED
			? RIPTIDE_SLOWNESS_TICKS_EMPOWERED
			: RIPTIDE_SLOWNESS_TICKS;
		defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, true, true, true));
		if (attacker instanceof ServerPlayer serverPlayer) {
			CombatFeedback.riptideDrag(serverPlayer, defender);
		}
	}

	private static void procCrucible(LivingEntity defender, Player attacker, CombatContext context) {
		Matchup matchup = matchupAgainst(Affinity.FORGE, defender, context);
		if (matchup == Matchup.NEUTRALIZED) {
			return;
		}
		int seconds = matchup == Matchup.EMPOWERED
			? CRUCIBLE_FIRE_SECONDS_EMPOWERED
			: CRUCIBLE_FIRE_SECONDS;
		defender.igniteForSeconds(seconds);
		if (attacker instanceof ServerPlayer serverPlayer) {
			CombatFeedback.crucibleIgnite(serverPlayer, defender);
		}
	}

	private static void procBloomward(LivingEntity defender, Player attacker, CombatContext context) {
		Matchup matchup = matchupAgainst(Affinity.VERDANT, defender, context);
		if (matchup == Matchup.NEUTRALIZED || attacker.isDeadOrDying()) {
			return;
		}
		float heal = matchup == Matchup.EMPOWERED ? BLOOMWARD_HEAL_EMPOWERED : BLOOMWARD_HEAL;
		attacker.heal(heal);
		if (attacker instanceof ServerPlayer serverPlayer) {
			CombatFeedback.bloomwardHeal(serverPlayer);
		}
	}

	private static void procGloaming(LivingEntity defender, Player attacker, CombatContext context) {
		Matchup matchup = matchupAgainst(Affinity.UMBRAL, defender, context);
		if (matchup == Matchup.NEUTRALIZED) {
			return;
		}
		int ticks = matchup == Matchup.EMPOWERED
			? GLOAMING_WEAKNESS_TICKS_EMPOWERED
			: GLOAMING_WEAKNESS_TICKS;
		defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, true, true, true));
		if (attacker instanceof ServerPlayer serverPlayer) {
			CombatFeedback.gloamingWeakness(serverPlayer, defender);
		}
	}

	public static boolean suppressesIncomingAdvantage(Player defender, LivingEntity attacker) {
		if (attacker == null) {
			return false;
		}
		if (isAt(defender, Capstone.STILLPOINT)
				&& Resonance.atApex(defender)
				&& hasAffinityPressure(attacker)) {
			return true;
		}
		return isAt(defender, Capstone.MAELSTROM)
			&& Resonance.atApex(defender)
			&& isScrambledBy(attacker, defender);
	}

	public static boolean suppressesIncomingAdvantage(Player defender, LivingEntity attacker,
			CombatContext context) {
		if (attacker == null) {
			return false;
		}
		if (context.isAt(defender, Capstone.STILLPOINT)
				&& context.atApex(defender)
				&& context.hasAffinityPressure(attacker)) {
			return true;
		}
		return context.isAt(defender, Capstone.MAELSTROM)
			&& context.atApex(defender)
			&& isScrambledBy(attacker, defender);
	}

	static boolean hasAffinityPressure(LivingEntity entity) {
		return CombatTargets.hasAffinity(entity);
	}

	private static boolean isApexMeleeTarget(LivingEntity defender, Player attacker, DamageSource source) {
		return isDirectMelee(attacker, source)
			&& CombatTargets.isHostileOrPvpOpponent(defender, attacker)
			&& canAffectApexTarget(defender, attacker)
			&& !isOwnPet(defender, attacker)
			&& !(defender instanceof AbstractVillager);
	}

	private static boolean canAffectApexTarget(LivingEntity defender, Player attackerPlayer) {
		return !(defender instanceof Player targetPlayer)
			|| CombatTargets.canAffectPlayer(attackerPlayer, targetPlayer);
	}

	private static boolean isDirectMelee(Player player, DamageSource source) {
		if (source.getDirectEntity() != player) {
			return false;
		}
		return !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
			&& !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
	}

	private static boolean isOwnPet(LivingEntity defender, Player attacker) {
		if (!(defender instanceof TamableAnimal pet)) {
			return false;
		}
		var ownerId = pet.getOwnerUUID();
		return ownerId != null && attacker.getUUID().equals(ownerId);
	}

	private static void markScrambled(Player player, LivingEntity target) {
		long expiresAt = target.level().getGameTime() + MAELSTROM_SCRAMBLE_TICKS;
		maelstromScrambles.put(new ScrambleKey(target.getUUID(), player.getUUID()), expiresAt);
	}

	private static boolean isScrambledBy(LivingEntity target, Player player) {
		ScrambleKey key = new ScrambleKey(target.getUUID(), player.getUUID());
		Long expiresAt = maelstromScrambles.get(key);
		if (expiresAt == null) {
			return false;
		}
		if (target.level().getGameTime() >= expiresAt) {
			maelstromScrambles.remove(key);
			return false;
		}
		return true;
	}

	private static void pulseStillpoint(Player player) {
		long now = player.level().getGameTime();
		Long last = stillpointPulses.get(player.getUUID());
		if (last != null && now - last < STILLPOINT_ABSORPTION_COOLDOWN_TICKS) {
			return;
		}
		stillpointPulses.put(player.getUUID(), now);
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
			STILLPOINT_ABSORPTION_TICKS, 0, true, true, true));
		if (player instanceof ServerPlayer serverPlayer) {
			CombatFeedback.stillpointPulse(serverPlayer);
		}
	}

	/**
	 * Fires the player's Apex identity ability if they are at an armed Maelstrom or
	 * Stillpoint capstone and off cooldown. Affinity capstones own no identity ability.
	 *
	 * @return {@code true} if an identity ability fired or reported its cooldown (the
	 *     ability key was consumed); {@code false} to let the caller fall through.
	 */
	public static boolean tryIdentityAbility(ServerPlayer player) {
		if (!Resonance.atApex(player)) {
			return false;
		}
		Capstone capstone = capstoneOf(player).orElse(null);
		if (capstone != Capstone.MAELSTROM && capstone != Capstone.STILLPOINT) {
			return false;
		}

		int baseCooldown = capstone == Capstone.MAELSTROM
			? MAELSTROM_NOVA_COOLDOWN_TICKS
			: STILLPOINT_FIELD_COOLDOWN_TICKS;
		int cooldownTicks = CombatMomentum.effectiveCooldown(
			baseCooldown, Resonance.killStreak(player), true);
		long now = player.level().getGameTime();
		Long readyAt = identityCooldowns.get(player.getUUID());
		if (readyAt != null && now < readyAt) {
			int remaining = (int) (readyAt - now);
			ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING, Component.translatable(
				"apex.attuned.identity_cooldown", cooldownSeconds(remaining)));
			return true;
		}

		ServerLevel level = (ServerLevel) player.level();
		if (capstone == Capstone.MAELSTROM) {
			fireMaelstromNova(player, level);
		} else {
			fireStillpointField(player, level);
		}
		identityCooldowns.put(player.getUUID(), now + cooldownTicks);
		return true;
	}

	/** Shaves ticks off an in-flight identity-ability cooldown (kill-streak momentum). */
	public static void shaveIdentityCooldown(ServerPlayer player, int ticks) {
		if (ticks <= 0) {
			return;
		}
		Long readyAt = identityCooldowns.get(player.getUUID());
		if (readyAt == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now >= readyAt) {
			identityCooldowns.remove(player.getUUID());
			return;
		}
		long newReady = Math.max(now, readyAt - ticks);
		if (newReady <= now) {
			identityCooldowns.remove(player.getUUID());
		} else {
			identityCooldowns.put(player.getUUID(), newReady);
		}
	}

	private static void fireMaelstromNova(ServerPlayer player, ServerLevel level) {
		AABB area = player.getBoundingBox().inflate(MAELSTROM_NOVA_RADIUS);
		List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, area, entity ->
			entity != player
				&& entity.isAlive()
				&& isApexNovaTarget(entity, player));
		for (LivingEntity victim : caught) {
			// knockback pushes opposite the (x, z) source vector, so passing the
			// player's position throws the victim outward, away from the nova.
			victim.knockback(MAELSTROM_NOVA_KNOCKBACK,
				player.getX() - victim.getX(), player.getZ() - victim.getZ());
			victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
				MAELSTROM_NOVA_WEAKNESS_TICKS, 0, true, true, true));
		}
		level.sendParticles(ParticleTypes.EXPLOSION,
			player.getX(), player.getY() + 1.0, player.getZ(), 8, 1.2, 0.6, 1.2, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
			player.getX(), player.getY() + 1.0, player.getZ(), 48, 1.6, 0.8, 1.6, 0.4);
		level.playSound(null, player.blockPosition(),
			SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9F, 1.4F);
		ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,
			Component.translatable("apex.attuned.maelstrom_nova"));
	}

	private static void fireStillpointField(ServerPlayer player, ServerLevel level) {
		AABB area = player.getBoundingBox().inflate(STILLPOINT_FIELD_RADIUS);
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, area, monster ->
			monster.isAlive());
		for (Monster monster : monsters) {
			monster.setTarget(null);
			monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
				STILLPOINT_FIELD_SLOWNESS_TICKS, 0, true, true, true));
		}
		level.sendParticles(ParticleTypes.END_ROD,
			player.getX(), player.getY() + 1.0, player.getZ(), 36, 1.8, 1.0, 1.8, 0.02);
		level.sendParticles(ParticleTypes.GLOW,
			player.getX(), player.getY() + 1.0, player.getZ(), 16, 1.6, 0.8, 1.6, 0.0);
		level.playSound(null, player.blockPosition(),
			SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.5F);
		ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,
			Component.translatable("apex.attuned.stillpoint_field"));
	}

	private static boolean isApexNovaTarget(LivingEntity entity, Player player) {
		if (entity instanceof Player targetPlayer) {
			return CombatTargets.canAffectPlayer(player, targetPlayer);
		}
		return entity instanceof Monster
			|| hasAffinityPressure(entity);
	}

	private static int cooldownSeconds(int remainingTicks) {
		if (remainingTicks <= 0) {
			return 0;
		}
		return (int) Math.max(1L, ((long) remainingTicks + 19L) / 20L);
	}

	public static boolean ignoresKnockback(LivingEntity entity) {
		return entity instanceof Player player
			&& isAt(player, Capstone.UNYIELDING)
			&& Resonance.atApex(player);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !player.isSprinting()) {
			return true;
		}
		if (!isAt(player, Capstone.UNTOUCHABLE) || !Resonance.atApex(player)) {
			return true;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == null) {
			return true;
		}
		if (!CombatTargets.isHostileOrPvpOpponent(attacker, player)) {
			return true;
		}
		Matchup matchup = matchupAgainst(Affinity.ZEPHYR, attacker);
		if (matchup == Matchup.NEUTRALIZED) {
			return true;
		}
		float chance = matchup == Matchup.EMPOWERED ? DODGE_EMPOWERED : DODGE_NORMAL;
		if (player.getRandom().nextFloat() >= chance) {
			return true;
		}
		onDodge(player);
		return false;
	}

	private static Matchup matchupAgainst(Affinity capstone, LivingEntity other) {
		Optional<Affinity> otherAffinity =
			other == null ? Optional.empty() : AttunedCombat.affinityOf(other);
		return matchupAgainst(capstone, otherAffinity);
	}

	private static Matchup matchupAgainst(Affinity capstone, LivingEntity other,
			CombatContext context) {
		Optional<Affinity> otherAffinity =
			other == null ? Optional.empty() : context.affinityOf(other);
		return matchupAgainst(capstone, otherAffinity);
	}

	private static Matchup matchupAgainst(Affinity capstone, Optional<Affinity> otherAffinity) {
		if (otherAffinity.isEmpty()) {
			return Matchup.NORMAL;
		}
		Affinity o = otherAffinity.get();
		if (o.beats(capstone)) {
			return Matchup.NEUTRALIZED;
		}
		if (capstone.beats(o)) {
			return Matchup.EMPOWERED;
		}
		return Matchup.NORMAL;
	}

	private static void tick(MinecraftServer server) {
		long nowTime = server.overworld().getGameTime();
		maelstromScrambles.entrySet().removeIf(entry -> nowTime >= entry.getValue());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Capstone now = capstoneOf(player).orElse(null);
			Capstone was = apexState.get(id);
			boolean armedNow = now != null && Resonance.atApex(player);
			boolean armedWas = Boolean.TRUE.equals(armedState.get(id));

			if (now == null && was == null) {
				continue;
			}
			if (now == null) {
				apexState.remove(id);
				armedState.remove(id);
				announceLost(player);
				continue;
			}
			if (was == null) {
				apexState.put(id, now);
				armedState.put(id, armedNow);
				if (armedNow) {
					announceGained(player, now);
				} else {
					announceGainedDormant(player, now);
				}
				continue;
			}
			if (was != now) {
				apexState.put(id, now);
				armedState.put(id, armedNow);
				announceLost(player);
				if (armedNow) {
					announceGained(player, now);
				} else {
					announceGainedDormant(player, now);
				}
				continue;
			}
			if (armedNow != armedWas) {
				armedState.put(id, armedNow);
				if (armedNow) {
					announceRearmed(player, now);
				} else {
					announceDormant(player);
				}
			}
		}
	}

	private static void announceGained(ServerPlayer player, Capstone capstone) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
		player.sendSystemMessage(Component.literal("Apex active: ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(capstone.displayName())
				.withStyle(capstone.chatColor(), ChatFormatting.BOLD))
			.append(Component.literal(". " + capstone.description())
				.withStyle(ChatFormatting.GRAY)));
		AttunedAdvancements.award(player, "attunement/apex");
	}

	private static void announceGainedDormant(ServerPlayer player, Capstone capstone) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 0.8F);
		player.sendSystemMessage(Component.translatable(
				"apex.attuned.unlocked_dormant",
				Component.literal(capstone.displayName())
					.withStyle(capstone.chatColor(), ChatFormatting.BOLD))
			.withStyle(ChatFormatting.GRAY));
	}

	private static void announceRearmed(ServerPlayer player, Capstone capstone) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.3F);
		ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,
			Component.translatable("apex.attuned.rearmed",
				Component.literal(capstone.displayName()).withStyle(capstone.chatColor(), ChatFormatting.BOLD)));
		AttunedAdvancements.award(player, "attunement/apex");
	}

	private static void announceDormant(ServerPlayer player) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 0.6F);
		ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
			Component.translatable("apex.attuned.dormant"));
	}

	private static void announceLost(ServerPlayer player) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.0F);
		player.sendSystemMessage(Component.literal("Your Apex has faded.")
			.withStyle(ChatFormatting.GRAY));
	}

	private static void onDodge(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, true));
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD,
			player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.blockPosition(),
			SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.7F);
	}

	private record ScrambleKey(UUID targetId, UUID playerId) {
	}
}
