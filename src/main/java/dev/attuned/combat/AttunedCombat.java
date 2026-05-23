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
	 * Scales one incoming hit by the affinity matchup and shows feedback for it.
	 * Invoked by {@code LivingEntityHurtMixin} before armour and absorption.
	 */
	public static float applyAffinity(ServerLevel level, LivingEntity defender,
			DamageSource source, float amount) {
		float multiplier = affinityMultiplier(defender, source);
		if (multiplier != 1.0F) {
			matchupFeedback(level, defender, source, multiplier);
		}
		return amount * multiplier;
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
