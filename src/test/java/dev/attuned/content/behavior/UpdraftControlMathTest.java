package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

/** Unit coverage for Updraft's explicit boost/brake elytra controls. */
class UpdraftControlMathTest {
	@Test
	void spaceBoostPushesForwardWithoutPitchSpeedControl() {
		Vec3 motion = new Vec3(0.9D, -0.05D, 0.0D);
		Vec3 level = UpdraftBehavior.controlledMotion(motion, new Vec3(1.0D, 0.0D, 0.0D),
			-90.0F, true, false);
		Vec3 lookUp = UpdraftBehavior.controlledMotion(motion, new Vec3(0.52D, 0.85D, 0.0D),
			-90.0F, true, false);

		assertTrue(level.x > motion.x);
		assertTrue(lookUp.x > motion.x);
		assertEquals(motion.y, level.y, 1.0E-9D);
		assertEquals(motion.y, lookUp.y, 1.0E-9D);
	}

	@Test
	void controlBrakeSlowsElytraFlightSignificantly() {
		Vec3 motion = new Vec3(1.2D, -0.22D, 0.0D);
		Vec3 next = UpdraftBehavior.controlledMotion(motion, new Vec3(1.0D, 0.0D, 0.0D),
			-90.0F, false, true);

		assertTrue(horizontalSpeed(next) < horizontalSpeed(motion) - 0.12D);
		assertTrue(Math.abs(next.y) < Math.abs(motion.y));
	}

	@Test
	void brakeOverridesBoostWhenBothControlsAreHeld() {
		Vec3 motion = new Vec3(1.0D, -0.05D, 0.0D);
		Vec3 next = UpdraftBehavior.controlledMotion(motion, new Vec3(1.0D, 0.0D, 0.0D),
			-90.0F, true, true);

		assertTrue(horizontalSpeed(next) < horizontalSpeed(motion));
	}

	@Test
	void pvpExhaustionStartsAfterFiveSecondsOfCombat() {
		assertTrue(!UpdraftBehavior.exhaustedByDuration(0L, 99L));
		assertTrue(UpdraftBehavior.exhaustedByDuration(0L, 100L));
	}

	@Test
	void boostEasesHardDirectionChangesInsteadOfSnappingVelocity() {
		Vec3 motion = new Vec3(0.9D, 0.0D, 0.0D);
		Vec3 lookBack = new Vec3(-1.0D, 0.0D, 0.0D);

		Vec3 next = UpdraftBehavior.controlledMotion(motion, lookBack, 90.0F, true, false);

		assertTrue(next.x > 0.75D);
		assertTrue(horizontalDelta(motion, next) <= UpdraftBehavior.MAX_HORIZONTAL_CHANGE);
	}

	@Test
	void boostDoesNotUsePitchAsAVerticalThrottle() {
		Vec3 motion = new Vec3(0.9D, -0.08D, 0.0D);
		Vec3 lookUp = new Vec3(0.52D, 0.85D, 0.0D);

		Vec3 next = UpdraftBehavior.controlledMotion(motion, lookUp, -90.0F, true, false);

		assertEquals(motion.y, next.y, 1.0E-9D);
	}

	private static double horizontalDelta(Vec3 a, Vec3 b) {
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double horizontalSpeed(Vec3 velocity) {
		return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
	}
}
