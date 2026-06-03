package dev.attuned.content.behavior;

import dev.attuned.AttunedConfig;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Voidstep Focus: triggering the Focus Ability keybind blinks the wearer a short
 * distance in the direction they are looking, on a cooldown.
 *
 * <p>The destination is the furthest unobstructed point up to {@link #MAX_DISTANCE}
 * blocks ahead; if nowhere along the look vector fits the player, the blink does
 * nothing. The per-player cooldown lives only in memory.
 */
public final class VoidstepBehavior implements FocusBehavior {

	/** Maximum blink distance, in blocks. */
	private static final int MAX_DISTANCE = 8;

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return AttunedConfig.get().voidstepCooldownTicks();
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.position();
		Vec3 look = player.getLookAngle();
		Vec3 destination = null;
		// Walk inward from the full distance to the first spot the player fits.
		for (int dist = MAX_DISTANCE; dist >= 1; dist--) {
			Vec3 candidate = origin.add(look.scale(dist));
			AABB box = player.getBoundingBox().move(candidate.subtract(origin));
			if (level.noCollision(player, box)) {
				destination = candidate;
				break;
			}
		}
		if (destination == null) {
			return false;
		}

		spawnTrail(level, origin);
		player.connection.teleport(destination.x, destination.y, destination.z,
			player.getYRot(), player.getXRot());
		player.resetFallDistance();
		spawnTrail(level, destination);
		level.playSound(null, player.blockPosition(),
			SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static void spawnTrail(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.6, 0.3, 0.4);
	}
}
