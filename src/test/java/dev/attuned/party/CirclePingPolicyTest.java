package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CirclePingPolicyTest {
	private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000201");

	@Test
	void validPingRequiresCircleMembershipLoadedVisibilityRangeAndCooldown() {
		CirclePingPolicy.PingContext valid = context(true, true, true, 24.0D, 100L);

		assertTrue(CirclePingPolicy.allowed(valid, 130L, 20L, 64.0D));
	}

	@Test
	void pingsCannotTrackTargetsAcrossDimensionsOrUnloadedChunks() {
		assertFalse(CirclePingPolicy.allowed(context(false, true, true, 24.0D, 100L),
			130L, 20L, 64.0D));
		assertFalse(CirclePingPolicy.allowed(context(true, false, true, 24.0D, 100L),
			130L, 20L, 64.0D));
	}

	@Test
	void pingsCannotRevealHiddenOrDistantTargets() {
		assertFalse(CirclePingPolicy.allowed(context(true, true, false, 24.0D, 100L),
			130L, 20L, 64.0D));
		assertFalse(CirclePingPolicy.allowed(context(true, true, true, 65.0D, 100L),
			130L, 20L, 64.0D));
	}

	@Test
	void pingsRequireCircleMembershipAndRespectRateLimit() {
		assertFalse(CirclePingPolicy.allowed(context(true, true, true, 24.0D, 100L, false),
			130L, 20L, 64.0D));
		assertFalse(CirclePingPolicy.allowed(context(true, true, true, 24.0D, 120L),
			130L, 20L, 64.0D));
	}

	private static CirclePingPolicy.PingContext context(boolean sameDimension, boolean targetLoaded,
			boolean targetVisible, double distanceBlocks, long lastPingTick) {
		return context(sameDimension, targetLoaded, targetVisible, distanceBlocks, lastPingTick, true);
	}

	private static CirclePingPolicy.PingContext context(boolean sameDimension, boolean targetLoaded,
			boolean targetVisible, double distanceBlocks, long lastPingTick, boolean sameCircle) {
		return new CirclePingPolicy.PingContext(SOURCE, sameCircle, sameDimension,
			targetLoaded, targetVisible, distanceBlocks * distanceBlocks, lastPingTick);
	}
}
