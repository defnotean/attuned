package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Executed coverage for Aegis's absorption recharge boundary — the exact off-by-one
 * surface that a source-grep contract test cannot protect. Drives the pure recharge
 * arithmetic that {@code onTick} delegates to.
 */
class AegisBehaviorRechargeTest {
	@Test
	void doesNotRechargeBeforeTheThresholdTick() {
		assertFalse(AegisBehavior.rechargesAfter(78),
			"78 + 1 = 79 active ticks must not yet recharge (threshold is 80).");
	}

	@Test
	void rechargesExactlyOnTheThresholdTick() {
		assertTrue(AegisBehavior.rechargesAfter(79),
			"79 + 1 = 80 active ticks reaches the recharge threshold.");
	}

	@Test
	void counterResetsOnRechargeAndIncrementsOtherwise() {
		assertEquals(0, AegisBehavior.advance(79), "Reaching the threshold resets the counter to 0.");
		assertEquals(79, AegisBehavior.advance(78), "Below the threshold the counter keeps climbing.");
		assertEquals(1, AegisBehavior.advance(0), "A fresh active tick increments from zero.");
	}
}
