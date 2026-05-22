package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The rock-paper-scissors counter-combat system.
 *
 * <p>Two effects layer onto every melee exchange between living entities:
 * <ol>
 *   <li><b>Affinity matchup</b> — damage is scaled when one combatant's
 *       {@link Affinity} counters the other's. Applied inside
 *       {@code LivingEntityHurtMixin}, which calls {@link #affinityMultiplier}.</li>
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

	private static final Identifier THORNWARD_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "thornward_focus");
	private static final Identifier LEECH_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "leech_focus");

	/** Re-entrancy guard so a reflected hit cannot trigger another reflection. */
	private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

	/** Registers the combat event handlers. Called from the mod initializer. */
	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(AttunedCombat::afterDamage);
	}

	/**
	 * The affinity-matchup damage multiplier for one attacker→defender exchange.
	 * Returns {@code 1.0} unless both combatants carry an affinity and one
	 * counters the other. Invoked by {@code LivingEntityHurtMixin}.
	 *
	 * @param defender the entity being hurt
	 * @param source   the damage source (its attacking entity is the attacker)
	 * @return the factor to multiply the raw damage amount by
	 */
	public static float affinityMultiplier(LivingEntity defender, DamageSource source) {
		LivingEntity attacker = attackerOf(source);
		if (attacker == null || attacker == defender) {
			return 1.0F;
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
	private static Optional<Affinity> affinityOf(LivingEntity entity) {
		if (entity instanceof Player player) {
			return Attunement.committedAffinity(player);
		}
		return MobAffinities.of(entity);
	}

	/**
	 * The living attacker behind a damage source, or {@code null}. Prefers the
	 * responsible entity (e.g. the shooter) and falls back to the direct entity.
	 */
	static LivingEntity attackerOf(DamageSource source) {
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
