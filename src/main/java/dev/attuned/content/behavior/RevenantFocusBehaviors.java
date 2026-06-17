package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Small Revenant faction behaviors for death-memory utility and spectral movement. */
public final class RevenantFocusBehaviors {
	private RevenantFocusBehaviors() {}

	public static final class Epitaph implements FocusBehavior {
		private static final double RADIUS = 7.0D;
		private static final double PULL = 0.085D;

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (!(player.level() instanceof ServerLevel level) || player.tickCount % 2 != 0) {
				return;
			}
			Vec3 target = player.position().add(0.0D, 0.65D, 0.0D);
			List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class,
				player.getBoundingBox().inflate(RADIUS), orb -> orb.isAlive());
			for (ExperienceOrb orb : orbs) {
				Vec3 pull = target.subtract(orb.position());
				double distance = pull.length();
				if (distance <= 0.35D) {
					continue;
				}
				Vec3 impulse = pull.normalize().scale(PULL / Math.max(1.0D, distance * 0.35D));
				orb.setDeltaMovement(orb.getDeltaMovement().scale(0.92D).add(impulse));
			}
		}
	}

	public static final class Hollowstep implements FocusBehavior {
		static final int COOLDOWN_TICKS = 180;
		static final int MAX_DISTANCE = 5;

		@Override
		public boolean hasActiveAbility() {
			return true;
		}

		@Override
		public int abilityCooldownTicks() {
			return COOLDOWN_TICKS;
		}

		@Override
		public boolean onAbility(ServerPlayer player, ItemStack focus) {
			ServerLevel level = (ServerLevel) player.level();
			Vec3 origin = player.position();
			Vec3 look = player.getLookAngle().normalize();
			Vec3 destination = null;
			for (double distance = MAX_DISTANCE; distance >= 1.0D; distance -= 0.5D) {
				Vec3 candidate = origin.add(look.scale(distance));
				if (canPhaseTo(level, player, origin, candidate)) {
					destination = candidate;
					break;
				}
			}
			if (destination == null) {
				return false;
			}

			spectralTrail(level, origin);
			player.connection.teleport(destination.x, destination.y, destination.z,
				player.getYRot(), player.getXRot());
			player.resetFallDistance();
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 35, 0, true, false, true));
			spectralTrail(level, destination);
			level.playSound(null, player.blockPosition(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 0.65F);
			return true;
		}

		private static void spectralTrail(ServerLevel level, Vec3 at) {
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.9D, at.z, 24, 0.35D, 0.55D, 0.35D, 0.07D);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.9D, at.z, 18, 0.25D, 0.45D, 0.25D, 0.08D);
		}

		private static boolean canPhaseTo(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 candidate) {
			if (pathBlocked(level, player, origin, candidate)) {
				return false;
			}
			AABB box = player.getBoundingBox().move(candidate.subtract(origin));
			return level.noCollision(player, box);
		}

		private static boolean pathBlocked(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 candidate) {
			double bodyY = Math.max(0.1D, player.getBbHeight() * 0.5D);
			HitResult hit = level.clip(new ClipContext(
				origin.add(0.0D, bodyY, 0.0D),
				candidate.add(0.0D, bodyY, 0.0D),
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				player));
			return hit.getType() == HitResult.Type.BLOCK;
		}
	}
}
