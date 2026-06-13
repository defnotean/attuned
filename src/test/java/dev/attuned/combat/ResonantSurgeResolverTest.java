package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Behavioral coverage for the pure resonant-surge resolver. Minecraft-free: the
 * inputs are game-time longs, the surge interval, the thunder flag, the online
 * player count, and flat {@code (dx, dz)} offsets against a radius.
 */
class ResonantSurgeResolverTest {

	@Test
	void surgeWaitsForTheFullIntervalBetweenStarts() {
		// lastSurgeEnd = 1000, interval = 12000 -> next eligible at 13000.
		assertFalse(ResonantSurgeResolver.shouldStart(12999L, 1000L, 12000L, true, 1),
			"A surge cannot start before the interval elapses since the last surge ended.");
		assertTrue(ResonantSurgeResolver.shouldStart(13000L, 1000L, 12000L, true, 1),
			"A surge becomes eligible once the full interval has elapsed.");
	}

	@Test
	void firstSurgeIsEligibleImmediatelyFromTheZeroBaseline() {
		assertTrue(ResonantSurgeResolver.shouldStart(12000L, 0L, 12000L, true, 1),
			"With no prior surge (baseline 0) the first surge is eligible after one interval.");
	}

	@Test
	void noOnlinePlayersMeansNoSurge() {
		assertFalse(ResonantSurgeResolver.shouldStart(50000L, 0L, 12000L, true, 0),
			"A surge needs at least one online player to anchor it.");
	}

	@Test
	void clearWeatherSuppressesSurges() {
		assertFalse(ResonantSurgeResolver.shouldStart(50000L, 0L, 12000L, false, 4),
			"A surge only happens during a thunderstorm.");
	}

	@Test
	void interiorPointsAreInsideAndTheRadiusEdgeCounts() {
		assertTrue(ResonantSurgeResolver.isInside(0.0, 0.0, 16),
			"The surge centre is inside the field.");
		assertTrue(ResonantSurgeResolver.isInside(16.0, 0.0, 16),
			"A point exactly at the radius (on-axis) counts as inside.");
		assertTrue(ResonantSurgeResolver.isInside(0.0, -16.0, 16),
			"A point exactly at the radius is inside regardless of sign.");
	}

	@Test
	void pointsBeyondTheRadiusAreOutside() {
		assertFalse(ResonantSurgeResolver.isInside(16.1, 0.0, 16),
			"A point just past the radius is outside the field.");
		assertFalse(ResonantSurgeResolver.isInside(12.0, 12.0, 16),
			"A diagonal point whose distance exceeds the radius is outside (12^2+12^2 > 16^2).");
	}

	@Test
	void diagonalPointWithinTheRadiusIsInside() {
		assertTrue(ResonantSurgeResolver.isInside(11.0, 11.0, 16),
			"A diagonal point within the radius is inside (11^2+11^2 = 242 <= 256).");
	}

	@Test
	void resonanceGainIsQuadrupled() {
		assertEquals(4.0F, ResonantSurgeResolver.resonanceGainMultiplier(), 0.0F,
			"Players inside a surge gain resonance at four times the base rate.");
	}
}
