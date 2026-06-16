package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.content.AttunedContent;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Updraft Focus: while fall-flying with a functional elytra, holding jump sends
 * smooth thrust along the look vector — faster than gliding alone, gentler and
 * more steerable than a firework rocket.
 */
public final class UpdraftBehavior implements FocusBehavior {
	/** Thrust added per tick while lift is held (blocks/tick). */
	static final double THRUST = 0.07D;
	/** Hard cap on total flight speed (blocks/tick). */
	static final double MAX_SPEED = 1.28D;
	/** Minimum horizontal speed while boosting so the glide never stalls out. */
	static final double MIN_HORIZONTAL = 0.12D;

	private static final Map<UUID, Boolean> LIFTING = new HashMap<>();

	static {
		AttunedServerCleanup.onStop(LIFTING::clear);
		AttunedPlayerCleanup.onForget(LIFTING::remove);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (!isLifting(player.getUUID())) {
			return;
		}
		if (!hasFunctionalElytra(player)) {
			setLifting(player.getUUID(), false);
			return;
		}
		if (!player.isFallFlying()) {
			if (canStartGlide(player)) {
				player.startFallFlying();
			} else {
				return;
			}
		}
		applyBoost(player);
	}

	public static void setLifting(UUID playerId, boolean lifting) {
		if (lifting) {
			LIFTING.put(playerId, true);
		} else {
			LIFTING.remove(playerId);
		}
	}

	static boolean isLifting(UUID playerId) {
		return LIFTING.getOrDefault(playerId, false);
	}

	public static boolean isActive(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.resolution(player).activeSlots()) {
			if (inv.get(slot).is(AttunedContent.UPDRAFT_FOCUS)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasFunctionalElytra(ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.is(Items.ELYTRA)) {
			return false;
		}
		return chest.getDamageValue() < chest.getMaxDamage() - 1;
	}

	private static boolean canStartGlide(ServerPlayer player) {
		return !player.onGround()
			&& !player.isInWater()
			&& !player.isPassenger()
			&& !player.isFallFlying()
			&& hasFunctionalElytra(player);
	}

	static void applyBoost(ServerPlayer player) {
		Vec3 look = player.getLookAngle().normalize();
		Vec3 motion = player.getDeltaMovement();
		Vec3 next = motion.add(look.scale(THRUST));

		double horizontal = Math.sqrt(next.x * next.x + next.z * next.z);
		if (horizontal < MIN_HORIZONTAL) {
			double yawRad = Math.toRadians(player.getYRot());
			next = next.add(-Math.sin(yawRad) * MIN_HORIZONTAL * 0.15D, 0.0D,
				Math.cos(yawRad) * MIN_HORIZONTAL * 0.15D);
		}

		double speed = next.length();
		if (speed > MAX_SPEED) {
			next = next.scale(MAX_SPEED / speed);
		}

		player.setDeltaMovement(next);
		player.resetFallDistance();
		player.fallDistance = 0.0F;

		if (!(player.level() instanceof ServerLevel level) || player.tickCount % 3 != 0) {
			return;
		}
		Vec3 trail = player.position().subtract(look.scale(0.6D)).add(0.0D, player.getBbHeight() * 0.45D, 0.0D);
		level.sendParticles(ParticleTypes.CLOUD, trail.x, trail.y, trail.z, 2, 0.08D, 0.05D, 0.08D, 0.01D);
		if (player.tickCount % 12 == 0) {
			level.playSound(null, player.blockPosition(),
				SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.12F, 1.65F);
		}
	}
}
