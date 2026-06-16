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
 * smooth forward thrust, while holding sprint/control brakes flight.
 */
public final class UpdraftBehavior implements FocusBehavior {
	/** Forward thrust added per tick while boost is held (blocks/tick). */
	static final double BOOST_THRUST = 0.08D;
	/** Highest speed the focus actively boosts toward. Faster vanilla motion is preserved. */
	static final double BOOST_SPEED_CAP = 1.45D;
	/** Target velocity multiplier while braking. */
	static final double BRAKE_FACTOR = 0.45D;
	/** Hard cap on total flight speed (blocks/tick). */
	static final double MAX_SPEED = 1.65D;
	/** Response factor toward the desired velocity; lower values feel smoother. */
	static final double MOTION_SMOOTHING = 0.65D;
	/** Maximum horizontal velocity change applied in one boost tick. */
	static final double MAX_HORIZONTAL_CHANGE = 0.09D;
	/** Maximum vertical velocity change applied in one boost tick. */
	static final double MAX_VERTICAL_CHANGE = 0.045D;
	/** Maximum horizontal velocity change applied in one brake tick. */
	static final double MAX_BRAKE_HORIZONTAL_CHANGE = 0.18D;
	/** Maximum vertical velocity change applied in one brake tick. */
	static final double MAX_BRAKE_VERTICAL_CHANGE = 0.12D;

	private static final Map<UUID, Controls> CONTROLS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_FLIGHT_TICK = new HashMap<>();

	static {
		AttunedServerCleanup.onStop(() -> {
			CONTROLS.clear();
			LAST_FLIGHT_TICK.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			CONTROLS.remove(uuid);
			LAST_FLIGHT_TICK.remove(uuid);
		});
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		tickFlight(player);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		setControls(player.getUUID(), false, false);
	}

	public static void setControls(UUID playerId, boolean boosting, boolean braking) {
		if (boosting || braking) {
			CONTROLS.put(playerId, new Controls(boosting, braking));
		} else {
			CONTROLS.remove(playerId);
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
			setControls(player.getUUID(), false, false);
			return;
		}
		Controls controls = controlsFor(player);
		if (!controls.active()) {
			return;
		}

		player.resetFallDistance();
		player.fallDistance = 0.0F;

		if (controls.boosting() && !player.isFallFlying() && canStartGlide(player)) {
			player.startFallFlying();
		}
		if (!player.isFallFlying()) {
			return;
		}
		applyFlightControls(player, controls);
	}

	public static boolean mitigatesFallDamage(ServerPlayer player) {
		return isActive(player) && hasFunctionalElytra(player) && controlsFor(player).active();
	}

	static Controls controlsFor(ServerPlayer player) {
		Controls controls = CONTROLS.get(player.getUUID());
		if (controls != null) {
			return controls;
		}
		return new Controls(player.getLastClientInput().jump(), false);
	}

	public static void applyFlightControls(ServerPlayer player, Controls controls) {
		Vec3 motion = player.getDeltaMovement();
		Vec3 look = player.getLookAngle();
		Vec3 next = controlledMotion(motion, look, player.getYRot(), controls.boosting(), controls.braking());

		player.setDeltaMovement(next);
		player.resetFallDistance();
		player.fallDistance = 0.0F;
		player.hurtMarked = true;
	}

	public static Vec3 controlledMotion(Vec3 motion, Vec3 look, double yawDegrees,
			boolean boosting, boolean braking) {
		if (braking) {
			return brakedMotion(motion);
		}
		if (boosting) {
			return boostedMotion(motion, look, yawDegrees);
		}
		return motion;
	}

	static Vec3 boostedMotion(Vec3 motion, Vec3 look, double yawDegrees) {
		Vec3 forward = horizontalLook(look, yawDegrees);
		double forwardSpeed = motion.x * forward.x + motion.z * forward.z;
		double targetForward = forwardSpeed >= BOOST_SPEED_CAP
			? forwardSpeed
			: Math.min(forwardSpeed + BOOST_THRUST, BOOST_SPEED_CAP);
		Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
		Vec3 lateral = horizontal.subtract(forward.scale(forwardSpeed));
		Vec3 boostedHorizontal = lateral.add(forward.scale(targetForward));
		Vec3 desired = new Vec3(
			boostedHorizontal.x,
			motion.y,
			boostedHorizontal.z
		);
		return capAddedSpeed(motion, smoothMotion(motion, desired,
			MAX_HORIZONTAL_CHANGE, MAX_VERTICAL_CHANGE));
	}

	static Vec3 brakedMotion(Vec3 motion) {
		Vec3 desired = motion.scale(BRAKE_FACTOR);
		return smoothMotion(motion, desired, MAX_BRAKE_HORIZONTAL_CHANGE, MAX_BRAKE_VERTICAL_CHANGE);
	}

	static Vec3 smoothMotion(Vec3 current, Vec3 desired, double maxHorizontalChange, double maxVerticalChange) {
		Vec3 horizontalDelta = new Vec3(desired.x - current.x, 0.0D, desired.z - current.z)
			.scale(MOTION_SMOOTHING);
		double horizontalChange = Math.sqrt(horizontalDelta.x * horizontalDelta.x
			+ horizontalDelta.z * horizontalDelta.z);
		if (horizontalChange > maxHorizontalChange) {
			horizontalDelta = horizontalDelta.scale(maxHorizontalChange / horizontalChange);
		}
		double verticalDelta = clamp((desired.y - current.y) * MOTION_SMOOTHING,
			-maxVerticalChange, maxVerticalChange);
		return new Vec3(
			current.x + horizontalDelta.x,
			current.y + verticalDelta,
			current.z + horizontalDelta.z
		);
	}

	private static Vec3 capAddedSpeed(Vec3 current, Vec3 next) {
		double nextSpeed = next.length();
		if (nextSpeed > MAX_SPEED && nextSpeed > current.length()) {
			next = next.scale(MAX_SPEED / nextSpeed);
		}
		return next;
	}

	private static boolean canStartGlide(ServerPlayer player) {
		return !player.onGround()
			&& !player.isInWater()
			&& !player.isPassenger()
			&& !player.isFallFlying()
			&& hasFunctionalElytra(player);
	}

	private static Vec3 horizontalLook(ServerPlayer player) {
		return horizontalLook(player.getLookAngle(), player.getYRot());
	}

	private static Vec3 horizontalLook(Vec3 look, double yawDegrees) {
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() < 1.0E-4D) {
			double yawRad = Math.toRadians(yawDegrees);
			return new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
		}
		return flat.normalize();
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Controls(boolean boosting, boolean braking) {
		boolean active() {
			return boosting || braking;
		}
	}
}
