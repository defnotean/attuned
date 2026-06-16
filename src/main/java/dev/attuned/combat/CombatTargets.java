package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.Attunement;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/** Shared target predicates for Attuned combat, PvP, and reveal effects. */
public final class CombatTargets {
	private CombatTargets() {}

	/** Whether {@code target} is a real PvP opponent the attacker's Focus may affect. */
	public static boolean canAffectPlayer(Player attacker, Player target) {
		return target != attacker
			&& target.isAlive()
			&& !target.isSpectator()
			&& attacker.getLevel() instanceof ServerLevel
			&& attacker.canHarmPlayer(target);
	}

	/** Whether the entity is either a hostile mob or a valid PvP opponent. */
	public static boolean isHostileOrPvpOpponent(LivingEntity target, Player player) {
		return isHostile(target) || isPvpOpponent(target, player);
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

	private static boolean isHostile(LivingEntity entity) {
		return entity.getType().getCategory() == MobCategory.MONSTER;
	}
}
