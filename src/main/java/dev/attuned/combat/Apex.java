package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The Apex capstone: a per-affinity passive that switches on only when a player
 * is fully committed to one affinity — at least {@link #MIN_FOCI} active Foci,
 * all sharing one affinity (no neutral Foci), with the attunement budget so
 * nearly spent that no further Focus could fit.
 *
 * <ul>
 *   <li><b>Fury — Execute:</b> the wearer's hits finish any mob already at or
 *       below {@link #EXECUTE_THRESHOLD} of its health.</li>
 *   <li><b>Bastion — Unyielding:</b> no single hit can take more than
 *       {@link #DAMAGE_CAP_FRACTION} of the wearer's max health, and knockback
 *       is ignored entirely (the knockback half lives in
 *       {@code LivingEntityKnockbackMixin}).</li>
 *   <li><b>Zephyr — Untouchable:</b> while sprinting, an incoming attack has a
 *       {@link #DODGE_CHANCE} chance to be dodged outright, with a burst of
 *       speed on the dodge.</li>
 * </ul>
 *
 * <p>The damage effects are applied from {@code LivingEntityHurtMixin} via
 * {@link #adjustDamage}; the dodge is an {@code ALLOW_DAMAGE} veto registered in
 * {@link #init}.
 */
public final class Apex {
	private Apex() {}

	/** Minimum active Foci for an Apex build. */
	private static final int MIN_FOCI = 4;
	/** Apex requires the budget within this many points of full. */
	private static final int BUDGET_SLACK = 1;

	/** Fury: mobs at or below this fraction of health are executed. */
	private static final float EXECUTE_THRESHOLD = 0.20F;
	/** Damage used to finish an executed mob — lethal through any armour. */
	private static final float EXECUTE_DAMAGE = 100000.0F;
	/** Bastion: the most of a player's max health one hit may remove. */
	private static final float DAMAGE_CAP_FRACTION = 0.15F;
	/** Zephyr: chance to dodge an attack while sprinting. */
	private static final float DODGE_CHANCE = 0.45F;

	/** Registers the Zephyr dodge veto. Called from the mod initializer. */
	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Apex::allowDamage);
	}

	/**
	 * The affinity a player has reached Apex in, if any. Apex requires at least
	 * {@link #MIN_FOCI} active Foci, every one of them on the same affinity (no
	 * neutral Foci), and the budget all but spent.
	 */
	public static Optional<Affinity> affinityOf(Player player) {
		List<Integer> active = Attunement.activeSlots(player);
		if (active.size() < MIN_FOCI) {
			return Optional.empty();
		}
		AttunedInv inv = AttunedAttachments.getInventory(player);
		Affinity shared = null;
		for (int slot : active) {
			Optional<Affinity> affinity = Attunement.definitionFor(player, inv.get(slot))
				.flatMap(FocusDefinition::affinity);
			if (affinity.isEmpty()) {
				return Optional.empty();
			}
			if (shared == null) {
				shared = affinity.get();
			} else if (shared != affinity.get()) {
				return Optional.empty();
			}
		}
		if (shared == null) {
			return Optional.empty();
		}
		if (Attunement.capacity(player) - Attunement.used(player) > BUDGET_SLACK) {
			return Optional.empty();
		}
		return Optional.of(shared);
	}

	/** Whether a player is at Apex in a specific affinity. */
	public static boolean isAt(Player player, Affinity affinity) {
		return affinityOf(player).filter(a -> a == affinity).isPresent();
	}

	/** The capstone's display name for an affinity. */
	public static String capstoneName(Affinity affinity) {
		return switch (affinity) {
			case FURY -> "Execute";
			case BASTION -> "Unyielding";
			case ZEPHYR -> "Untouchable";
		};
	}

	/**
	 * Applies the damage-shaping capstones to one hit: the Bastion cap on a
	 * defending player, and the Fury execute when an Apex player strikes a
	 * low-health mob. Called from {@code LivingEntityHurtMixin}.
	 */
	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		// Bastion — Unyielding: cap how much one hit can remove.
		if (defender instanceof Player defenderPlayer && isAt(defenderPlayer, Affinity.BASTION)) {
			float cap = defenderPlayer.getMaxHealth() * DAMAGE_CAP_FRACTION;
			if (amount > cap) {
				amount = cap;
			}
		}
		// Fury — Execute: an Apex wearer finishes a low-health mob.
		if (!(defender instanceof Player) && defender.getMaxHealth() > 0.0F
				&& defender.getHealth() / defender.getMaxHealth() <= EXECUTE_THRESHOLD
				&& AttunedCombat.attackerOf(source) instanceof Player attacker
				&& isAt(attacker, Affinity.FURY)) {
			amount = Math.max(amount, EXECUTE_DAMAGE);
		}
		return amount;
	}

	/** Whether knockback against this entity should be ignored (Bastion Apex). */
	public static boolean ignoresKnockback(LivingEntity entity) {
		return entity instanceof Player player && isAt(player, Affinity.BASTION);
	}

	/** {@code ALLOW_DAMAGE} veto implementing the Zephyr dodge. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !player.isSprinting()) {
			return true;
		}
		if (!isAt(player, Affinity.ZEPHYR)) {
			return true;
		}
		// Only dodge attacks from something — never fall, fire, drowning or the void.
		if (source.getEntity() == null && source.getDirectEntity() == null) {
			return true;
		}
		if (player.getRandom().nextFloat() >= DODGE_CHANCE) {
			return true;
		}
		onDodge(player);
		return false;
	}

	private static void onDodge(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, true, false, true));
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD,
			player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.blockPosition(),
			SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.7F);
	}
}
