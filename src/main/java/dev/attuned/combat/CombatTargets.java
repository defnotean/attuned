package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.Attunement;
import dev.attuned.party.CircleRuntime;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/** Shared target predicates for Attuned combat, PvP, and reveal effects. */
public final class CombatTargets {
	private CombatTargets() {}

	/** Whether {@code target} is a real PvP opponent the attacker's Focus may affect. */
	public static boolean canAffectPlayer(Player attacker, Player target) {
		return target != attacker
			&& target.isAlive()
			&& !target.isSpectator()
			&& attacker.level() instanceof ServerLevel
			&& pvpEnabled(attacker)
			&& !sameCircle(attacker, target)
			&& attacker.canHarmPlayer(target);
	}

	/** Whether a player kill can receive hostile PvP credit after the victim has died. */
	public static boolean canCreditPvpKill(Player attacker, Player target) {
		return target != attacker
			&& !target.isSpectator()
			&& attacker.level() instanceof ServerLevel
			&& pvpEnabled(attacker)
			&& !sameCircle(attacker, target)
			&& attacker.canHarmPlayer(target);
	}

	/** Whether the entity is either a hostile mob or a valid PvP opponent. */
	public static boolean isHostileOrPvpOpponent(LivingEntity target, Player player) {
		return (!isOwnedOrAllied(target, player) && isHostile(target, player))
			|| isPvpOpponent(target, player);
	}

	/** The single combat affinity carried by the entity, if it has one. */
	public static Optional<Affinity> affinityOf(LivingEntity entity) {
		if (entity instanceof Player player) {
			return Attunement.committedAffinity(player);
		}
		return MobAffinities.of(entity);
	}

	/** Whether the entity exerts affinity pressure in combat, including Discord players. */
	public static boolean hasAffinity(LivingEntity entity) {
		return affinityOf(entity).isPresent()
			|| (entity instanceof Player player && Attunement.isDiscord(player));
	}

	private static boolean isPvpOpponent(LivingEntity target, Player player) {
		return target instanceof Player targetPlayer
			&& canAffectPlayer(player, targetPlayer);
	}

	private static boolean sameCircle(Player attacker, Player target) {
		return CircleRuntime.manager().circleOf(attacker.getUUID())
			.map(circle -> circle.members().contains(target.getUUID()))
			.orElse(false);
	}

	private static boolean pvpEnabled(Player player) {
		return player.getServer() != null && player.getServer().isPvpAllowed();
	}

	private static boolean isHostile(LivingEntity entity, Player player) {
		if (entity.getType().getCategory() == MobCategory.MONSTER) {
			return true;
		}
		if (entity.getLastHurtByMob() == player) {
			return true;
		}
		return entity instanceof Mob mob && mob.getTarget() == player;
	}

	private static boolean isOwnedOrAllied(LivingEntity target, Player player) {
		if (target.isAlliedTo(player)) {
			return true;
		}
		return target instanceof OwnableEntity ownable
			&& ownable.getOwnerUUID() != null;
	}
}
