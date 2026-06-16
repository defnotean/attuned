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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Updraft Focus: while fall-flying with a functional elytra, holding jump adds
 * strong forward thrust along horizontal look after vanilla movement resolves.
 */
public final class UpdraftBehavior implements FocusBehavior {
	/** Forward thrust added per tick while boost is held (blocks/tick). */
	static final double THRUST = 0.24D;
	/** Extra vertical push per tick at full look-up; scaled by look pitch. */
	static final double VERTICAL_THRUST = 0.04D;
	/** Hard cap on total flight speed (blocks/tick). */
	static final double MAX_SPEED = 2.4D;
	/** Minimum horizontal speed while boosting so the glide never stalls out. */
	static final double MIN_HORIZONTAL = 0.6D;

	private static final Map<UUID, Boolean> LIFTING = new HashMap<>();
	private static final Map<UUID, Integer> LAST_FLIGHT_TICK = new HashMap<>();

	static {
		AttunedServerCleanup.onStop(() -> {
			LIFTING.clear();
			LAST_FLIGHT_TICK.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			LIFTING.remove(uuid);
			LAST_FLIGHT_TICK.remove(uuid);
		});
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		tickFlight(player);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		setLifting(player.getUUID(), false);
	}

	public static void setLifting(UUID playerId, boolean lifting) {
		if (lifting) {
			LIFTING.put(playerId, true);
		} else {
			LIFTING.remove(playerId);
		}
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

	public static void tickFlight(ServerPlayer player) {
		if (player.tickCount == LAST_FLIGHT_TICK.getOrDefault(player.getUUID(), -1)) {
			return;
		}
		LAST_FLIGHT_TICK.put(player.getUUID(), player.tickCount);

		if (!isActive(player) || !hasFunctionalElytra(player)) {
			setLifting(player.getUUID(), false);
			return;
		}
		if (!wantsBoost(player)) {
			return;
		}

		player.resetFallDistance();
		player.fallDistance = 0.0F;

		if (!player.isFallFlying() && canStartGlide(player)) {
			player.startFallFlying();
		}
		if (!player.isFallFlying()) {
			return;
		}
		applyFlightBoost(player);
	}

	public static boolean mitigatesFallDamage(ServerPlayer player) {
		return isActive(player) && hasFunctionalElytra(player) && wantsBoost(player);
	}

	static boolean wantsBoost(ServerPlayer player) {
		if (LIFTING.getOrDefault(player.getUUID(), false)) {
			return true;
		}
		return player.getLastClientInput().jump();
	}

	public static void applyFlightBoost(ServerPlayer player) {
		Vec3 forward = horizontalLook(player);
		Vec3 motion = player.getDeltaMovement();
		Vec3 look = player.getLookAngle();

		double forwardSpeed = motion.x * forward.x + motion.z * forward.z;
		double targetForward = Math.max(forwardSpeed + THRUST, MIN_HORIZONTAL);
		Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
		Vec3 lateral = horizontal.subtract(forward.scale(forwardSpeed));
		Vec3 boostedHorizontal = lateral.add(forward.scale(targetForward));

		Vec3 next = new Vec3(
			boostedHorizontal.x,
			motion.y + look.y * VERTICAL_THRUST,
			boostedHorizontal.z
		);

		double speed = next.length();
		if (speed > MAX_SPEED) {
			next = next.scale(MAX_SPEED / speed);
		}

		player.setDeltaMovement(next);
		player.resetFallDistance();
		player.fallDistance = 0.0F;
		player.hurtMarked = true;
	}

	private static boolean canStartGlide(ServerPlayer player) {
		return !player.onGround()
			&& !player.isInWater()
			&& !player.isPassenger()
			&& !player.isFallFlying()
			&& hasFunctionalElytra(player);
	}

	private static Vec3 horizontalLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() < 1.0E-4D) {
			double yawRad = Math.toRadians(player.getYRot());
			return new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
		}
		return flat.normalize();
	}
}
