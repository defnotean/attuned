package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.content.AttunedContent;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.network.ActionBarMessages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
	/** Five seconds at 20 TPS before PvP flight control exhaustion begins. */
	static final int PVP_EXHAUSTION_TICKS = 100;
	/** How long a PvP combat window remains active after the last player hit. */
	static final int PVP_COMBAT_GRACE_TICKS = 120;
	private static final int FLIGHT_EFFECT_INTERVAL = 3;
	private static final int FLIGHT_SOUND_INTERVAL = 12;
	private static final int EXHAUSTION_EFFECT_INTERVAL = 20;
	private static final int EXHAUSTION_DEBUFF_TICKS = 40;

	private static final Map<UUID, Controls> CONTROLS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_FLIGHT_TICK = new HashMap<>();
	private static final Map<UUID, Long> PVP_STARTED = new HashMap<>();
	private static final Map<UUID, Long> PVP_LAST = new HashMap<>();

	static {
		AttunedServerCleanup.onStop(() -> {
			CONTROLS.clear();
			LAST_FLIGHT_TICK.clear();
			PVP_STARTED.clear();
			PVP_LAST.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			CONTROLS.remove(uuid);
			LAST_FLIGHT_TICK.remove(uuid);
			PVP_STARTED.remove(uuid);
			PVP_LAST.remove(uuid);
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
		if (isPvpExhausted(player)) {
			applyPvpExhaustion(player);
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
		return isActive(player) && hasFunctionalElytra(player)
			&& controlsFor(player).active() && !isPvpExhausted(player);
	}

	public static void recordPvpDamage(LivingEntity defender, DamageSource source) {
		if (!(defender instanceof ServerPlayer victim)
				|| !(source.getEntity() instanceof ServerPlayer attacker)
				|| attacker == victim) {
			return;
		}
		long now = victim.getLevel().getGameTime();
		markPvpCombat(victim, now);
		markPvpCombat(attacker, now);
	}

	static Controls controlsFor(ServerPlayer player) {
		Controls controls = CONTROLS.get(player.getUUID());
		if (controls != null) {
			return controls;
		}
		return new Controls(false, false);
	}

	public static void applyFlightControls(ServerPlayer player, Controls controls) {
		Vec3 motion = player.getDeltaMovement();
		Vec3 look = player.getLookAngle();
		Vec3 next = controlledMotion(motion, look, player.getYRot(), controls.boosting(), controls.braking());

		player.setDeltaMovement(next);
		player.resetFallDistance();
		player.fallDistance = 0.0F;
		player.hurtMarked = true;
		spawnFlightEffects(player, controls);
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

	private static void spawnFlightEffects(ServerPlayer player, Controls controls) {
		if (player.tickCount % FLIGHT_EFFECT_INTERVAL != 0) {
			return;
		}
		ServerLevel level = player.getLevel();
		if (controls.braking()) {
			spawnBrakeEffects(level, player);
		} else if (controls.boosting()) {
			spawnBoostEffects(level, player);
		}
	}

	private static void spawnBoostEffects(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 trail = player.position().subtract(look.scale(0.75D))
			.add(0.0D, player.getBbHeight() * 0.45D, 0.0D);
		level.sendParticles(ParticleTypes.CLOUD,
			trail.x, trail.y, trail.z, 1, 0.08D, 0.05D, 0.08D, 0.01D);
		level.sendParticles(ParticleTypes.CLOUD,
			trail.x, trail.y, trail.z, 2, 0.12D, 0.06D, 0.12D, 0.02D);
		if (player.tickCount % FLIGHT_SOUND_INTERVAL == 0) {
			level.playSound(null, player.blockPosition(),
				SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.22F, 1.65F);
		}
	}

	private static void spawnBrakeEffects(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 cushion = player.position().add(look.scale(0.55D))
			.add(0.0D, player.getBbHeight() * 0.45D, 0.0D);
		level.sendParticles(ParticleTypes.CLOUD,
			cushion.x, cushion.y, cushion.z, 5, 0.22D, 0.10D, 0.22D, 0.02D);
		level.sendParticles(ParticleTypes.POOF,
			cushion.x, cushion.y, cushion.z, 2, 0.16D, 0.08D, 0.16D, 0.01D);
		if (player.tickCount % FLIGHT_SOUND_INTERVAL == 0) {
			level.playSound(null, player.blockPosition(),
				SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.18F, 0.72F);
		}
	}

	private static void markPvpCombat(ServerPlayer player, long now) {
		UUID id = player.getUUID();
		Long last = PVP_LAST.get(id);
		if (last == null || now - last > PVP_COMBAT_GRACE_TICKS) {
			PVP_STARTED.put(id, now);
		}
		PVP_LAST.put(id, now);
	}

	private static boolean isPvpExhausted(ServerPlayer player) {
		UUID id = player.getUUID();
		Long started = PVP_STARTED.get(id);
		Long last = PVP_LAST.get(id);
		if (started == null || last == null) {
			return false;
		}
		long now = player.getLevel().getGameTime();
		if (now - last > PVP_COMBAT_GRACE_TICKS) {
			PVP_STARTED.remove(id);
			PVP_LAST.remove(id);
			return false;
		}
		return exhaustedByDuration(started, now);
	}

	static boolean isPvpAssistDampened(ServerPlayer player) {
		return isPvpExhausted(player);
	}

	static boolean exhaustedByDuration(long startedTick, long nowTick) {
		return nowTick - startedTick >= PVP_EXHAUSTION_TICKS;
	}

	private static void applyPvpExhaustion(ServerPlayer player) {
		if (player.isFallFlying()) {
			player.setDeltaMovement(brakedMotion(player.getDeltaMovement()));
			player.hurtMarked = true;
		}
		if (player.tickCount % EXHAUSTION_EFFECT_INTERVAL != 0) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
			EXHAUSTION_DEBUFF_TICKS, 0, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
			EXHAUSTION_DEBUFF_TICKS, 0, true, true, true));
		ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
			Component.translatable("item.attuned.updraft_focus.exhausted"));
		ServerLevel level = player.getLevel();
		Vec3 at = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
		level.sendParticles(ParticleTypes.SMOKE,
			at.x, at.y, at.z, 8, 0.25D, 0.20D, 0.25D, 0.015D);
		level.sendParticles(ParticleTypes.POOF,
			at.x, at.y, at.z, 4, 0.20D, 0.12D, 0.20D, 0.01D);
		level.playSound(null, player.blockPosition(),
			SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.45F, 0.65F);
	}

	private static boolean canStartGlide(ServerPlayer player) {
		return !player.isOnGround()
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
