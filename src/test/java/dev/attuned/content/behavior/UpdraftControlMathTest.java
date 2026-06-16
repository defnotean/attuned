package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit coverage for Updraft's pitch-controlled elytra speed curve. */
class UpdraftControlMathTest {
	@Test
	void lookingUpTradesForwardSpeedForLift() {
		double speed = 0.9D;

		assertTrue(UpdraftBehavior.controlledForwardSpeed(speed, 0.85D) < speed);
		assertTrue(UpdraftBehavior.controlledVerticalBoost(0.85D) > 0.0D);
	}

	@Test
	void lookingDownAcceleratesWithinTheControlledBand() {
		double speed = 0.9D;
		double next = UpdraftBehavior.controlledForwardSpeed(speed, -0.85D);

		assertTrue(next > speed);
		assertTrue(next <= UpdraftBehavior.DIVE_SPEED_CAP);
	}

	@Test
	void levelFlightAddsOnlyModestCruiseThrust() {
		double speed = 0.9D;
		double next = UpdraftBehavior.controlledForwardSpeed(speed, 0.0D);

		assertTrue(next > speed);
		assertTrue(next - speed < 0.06D);
	}
}
