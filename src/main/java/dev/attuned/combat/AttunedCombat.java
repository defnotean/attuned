package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rock-paper-scissors counter-combat system.
 *
 * <p>Two effects layer onto every melee exchange between living entities:
 * <ol>
 *   <li><b>Affinity matchup</b> — damage is scaled when one combatant's
 *       {@link Affinity} counters the other's. Applied inside
 *       {@code LivingEntityHurtMixin}, which calls {@link #applyAffinity}.</li>
 *   <li><b>Focus procs</b> — the Thornward and Leech Foci have no attribute
 *       modifiers; their entire effect (reflect / lifesteal) fires from the
 *       {@code AFTER_DAMAGE} event handler registered in {@link #init()}.</li>
 * </ol>
 *
 * <p>The matchup scaling deliberately lives in a Mixin rather than an event:
 * the Fabric API in use exposes only {@code ALLOW_DAMAGE} (a veto) and
 * {@code AFTER_DAMAGE} (post-application) — neither can rescale the incoming
 * amount. The Mixin modifies the {@code float} amount argument of
 * {@code LivingEntity.hurtServer} before armour and absorption are applied.
 */
public final class AttunedCombat {
	private AttunedCombat() {}

	/** Damage multiplier when the attacker counters the defender. */
	private static final float ADVANTAGE_MULTIPLIER = 1.33F;
	/** Damage multiplier when the defender counters the attacker. */
	private static final float DISADVANTAGE_MULTIPLIER = 0.75F;

	/** Fraction of incoming damage a Thornward defender reflects to the attacker. */
	private static final float THORNWARD_REFLECT = 0.25F;
	/** Fraction of damage dealt a Leech attacker recovers as health. */
	private static final float LEECH_LIFESTEAL = 0.20F;
	/** Extra direct-melee damage Cinder deals to burning enemies. */
	private static final float CINDER_BURNING_BONUS = 0.20F;
	/** Extra fully charged direct-melee damage Sunlance deals to its marked prey. */
	private static final float SUNLANCE_BONUS = 0.10F;
	/** Sunlance asks for a deliberate swing, not spam-click pressure. */
	private static final float SUNLANCE_CHARGED_SWING_THRESHOLD = 0.9F;

	private static final Identifier THORNWARD_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "thornward_focus");
	private static final Identifier LEECH_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "leech_focus");
	private static final Identifier CINDER_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "cinder_focus");
	private static final Identifier SUNLANCE_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "sunlance_focus");

	/** Re-entrancy guard so a reflected hit cannot trigger another reflection. */
	private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);
	/** Last game-time a mob affinity spark was shown for an entity. */
	private static final Map<UUID, Long> LAST_AFFINITY_SPARK = new HashMap<>();

	/** Registers the combat event handlers. Called from the mod initializer. */
	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(AttunedCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
			LAST_AFFINITY_SPARK.remove(entity.getUUID()));
	}

	/**
	 * Scales one incoming hit by the affinity matchup and shows feedback for it.
	 * Invoked by {@code LivingEntityHurtMixin} before armour and absorption.
	 */
	public static float applyAffinity(ServerLevel level, LivingEntity defender,
			DamageSource source, float amount) {
		float multiplier = affinityMultiplier(defender, source);
		if (multiplier != 1.0F) {
			matchupFeedback(level, defender, source, multiplier);
		}
		if (attackerOf(source) instanceof Player && !(defender instanceof Player)) {
			mobAffinitySpark(level, defender);
		}
		float adjusted = amount * multiplier;
		if (cinderApplies(defender, source)) {
			adjusted *= (1.0F + CINDER_BURNING_BONUS);
		}
		if (sunlanceApplies(defender, source)) {
			adjusted *= (1.0F + SUNLANCE_BONUS);
		}
		return adjusted;
	}

	private static boolean cinderApplies(LivingEntity defender, DamageSource source) {
		if (!(attackerOf(source) instanceof Player player) || source.getDirectEntity() != player) {
			return false;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		return defender.isOnFire() && hasActiveFocus(player, CINDER_FOCUS);
	}

	private static boolean sunlanceApplies(LivingEntity defender, DamageSource source) {
		if (!(attackerOf(source) instanceof Player player) || !isDirectChargedMelee(player, source,
				SUNLANCE_CHARGED_SWING_THRESHOLD) || !hasActiveFocus(player, SUNLANCE_FOCUS)) {
			return false;
		}
		return defender.typeHolder().is(EntityTypeTags.UNDEAD)
			|| MobAffinities.of(defender).filter(affinity -> affinity == Affinity.FURY).isPresent();
	}

	private static boolean isDirectChargedMelee(Player player, DamageSource source, float threshold) {
		if (source.getDirectEntity() != player) {
			return false;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		return player.getAttackStrengthScale(0.5F) >= threshold;
	}

	private static void mobAffinitySpark(ServerLevel level, LivingEntity entity) {
		Optional<Affinity> affinity = MobAffinities.of(entity);
		if (affinity.isEmpty()) {
			return;
		}
		UUID id = entity.getUUID();
		long now = level.getGameTime();
		Long last = LAST_AFFINITY_SPARK.get(id);
		if (last != null && now - last < 40L) {
			return;
		}
		LAST_AFFINITY_SPARK.put(id, now);
		level.sendParticles(new DustParticleOptions(affinity.get().argb() & 0x00FFFFFF, 0.8F),
			entity.getX(), entity.getY() + entity.getBbHeight() * 0.7, entity.getZ(),
			4, 0.2, 0.25, 0.2, 0.0);
	}

	/**
	 * The affinity-matchup damage multiplier for one attacker→defender exchange.
	 * A combatant in Discord both deals and takes the advantage multiplier
	 * against anyone; otherwise the rock-paper-scissors cycle applies, and a
	 * matchup with no counter (or an unattuned combatant) leaves damage at 1.0.
	 */
	public static float affinityMultiplier(LivingEntity defender, DamageSource source) {
		LivingEntity attacker = attackerOf(source);
		if (attacker == null || attacker == defender) {
			return 1.0F;
		}
		if (defender instanceof Player defenderPlayer
				&& Apex.suppressesIncomingAdvantage(defenderPlayer, attacker)) {
			return 1.0F;
		}
		// Discord — clashing affinities — is a glass cannon on both ends.
		if (isDiscord(attacker) || isDiscord(defender)) {
			return ADVANTAGE_MULTIPLIER;
		}
		Optional<Affinity> attackerAffinity = affinityOf(attacker);
		Optional<Affinity> defenderAffinity = affinityOf(defender);
		if (attackerAffinity.isEmpty() || defenderAffinity.isEmpty()) {
			return 1.0F;
		}
		Affinity atk = attackerAffinity.get();
		Affinity def = defenderAffinity.get();
		if (atk.beats(def)) {
			return ADVANTAGE_MULTIPLIER;
		}
		if (def.beats(atk)) {
			return DISADVANTAGE_MULTIPLIER;
		}
		return 1.0F;
	}

	/** Whether a combatant is a player in the Discord stance. */
	private static boolean isDiscord(LivingEntity entity) {
		return entity instanceof Player player && Attunement.isDiscord(player);
	}

	/**
	 * Shows the rock-paper-scissors outcome of a hit: an affinity-coloured spark
	 * and a sharp sound on an advantage, a dull puff and a weak sound on a
	 * disadvantage — so players can read the cycle.
	 */
	private static void matchupFeedback(ServerLevel level, LivingEntity defender,
			DamageSource source, float multiplier) {
		double x = defender.getX();
		double y = defender.getY() + defender.getBbHeight() * 0.6;
		double z = defender.getZ();
		if (multiplier > 1.0F) {
			LivingEntity attacker = attackerOf(source);
			int color = attacker != null ? matchupColor(attacker) : 0xFFFFFF;
			level.sendParticles(new DustParticleOptions(color, 1.0F), x, y, z, 10, 0.3, 0.3, 0.3, 0.0);
			level.playSound(null, defender.blockPosition(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 1.2F);
		} else {
			level.sendParticles(ParticleTypes.POOF, x, y, z, 8, 0.25, 0.25, 0.25, 0.0);
			level.playSound(null, defender.blockPosition(),
				SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
	}

	/** The feedback colour for an attacker: its affinity colour, or the Discord magenta. */
	private static int matchupColor(LivingEntity attacker) {
		if (isDiscord(attacker)) {
			return AffinityColors.DISCORD_RGB;
		}
		return affinityOf(attacker).map(AttunedCombat::affinityColor).orElse(0xFFFFFF);
	}

	/** The 24-bit RGB form of an affinity's display colour, for {@code DustParticleOptions}. */
	private static int affinityColor(Affinity affinity) {
		return affinity.argb() & 0x00FFFFFF;
	}

	/**
	 * Post-damage handler: applies the Thornward reflect and Leech lifesteal
	 * procs. {@code dealtDamage} is the damage actually taken after armour and
	 * absorption — both procs scale off the real figure.
	 */
	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || REFLECTING.get()) {
			return;
		}
		LivingEntity attacker = attackerOf(source);
		if (attacker == null || attacker == defender) {
			return;
		}

		// Thornward: the defender reflects a fraction of the hit back.
		if (defender instanceof Player defenderPlayer
				&& hasActiveFocus(defenderPlayer, THORNWARD_FOCUS)
				&& attacker.isAlive()) {
			float reflected = dealtDamage * THORNWARD_REFLECT;
			if (reflected > 0.0F && attacker.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				REFLECTING.set(true);
				try {
					attacker.hurtServer(
						serverLevel,
						defender.damageSources().thorns(defenderPlayer),
						reflected
					);
				} finally {
					REFLECTING.set(false);
				}
			}
		}

		// Leech: the attacker heals for a fraction of the damage it dealt.
		if (attacker instanceof Player attackerPlayer
				&& hasActiveFocus(attackerPlayer, LEECH_FOCUS)
				&& !attackerPlayer.isDeadOrDying()) {
			attackerPlayer.heal(dealtDamage * LEECH_LIFESTEAL);
		}
	}

	/** The committed affinity of a living entity — player attunement or mob mapping. */
	static Optional<Affinity> affinityOf(LivingEntity entity) {
		if (entity instanceof Player player) {
			return Attunement.committedAffinity(player);
		}
		return MobAffinities.of(entity);
	}

	/**
	 * The living attacker behind a damage source, or {@code null}. Prefers the
	 * responsible entity (e.g. the shooter) and falls back to the direct entity.
	 */
	public static LivingEntity attackerOf(DamageSource source) {
		Entity entity = source.getEntity();
		if (entity instanceof LivingEntity living) {
			return living;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	/**
	 * Whether the player has an active (in-budget, on-affinity) Focus whose item
	 * is registered under {@code targetId}.
	 */
	private static boolean hasActiveFocus(Player player, Identifier targetId) {
		AttunedInv inventory = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			if (targetId.equals(itemId)) {
				return true;
			}
		}
		return false;
	}
}
