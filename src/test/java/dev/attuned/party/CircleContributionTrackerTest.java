package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleContributionTrackerTest {
	private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000811");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000812");

	@Test
	void trackerDefaultsUnknownMembersOutsideSharedCreditWindow() {
		CircleContributionTracker tracker = new CircleContributionTracker();
		CircleContributionPolicy.MemberContext context =
			context(tracker, MEMBER, true, true, true, true, false, 8.0D, 1000L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(context, 1000L, 200L, 48.0D),
			"Members with no recorded contribution should never qualify for shared credit.");
	}

	@Test
	void trackerRecordsLatestContributionAndIgnoresOlderTicks() {
		CircleContributionTracker tracker = new CircleContributionTracker();

		tracker.record(MEMBER, 1000L);
		tracker.record(MEMBER, 900L);

		assertEquals(1000L, tracker.lastContributionTick(MEMBER));
		assertTrue(CircleContributionPolicy.eligibleForSharedCredit(
			context(tracker, MEMBER, true, true, true, true, false, 8.0D, 1005L),
			1005L, 200L, 48.0D));
	}

	@Test
	void forgetClearAndPruneDropStaleContributionState() {
		CircleContributionTracker tracker = new CircleContributionTracker();
		tracker.record(MEMBER, 1000L);
		tracker.record(SECOND, 1050L);

		tracker.forget(MEMBER);
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(tracker, MEMBER, true, true, true, true, false, 8.0D, 1100L),
			1100L, 200L, 48.0D));

		tracker.prune(1301L, 200L);
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(tracker, SECOND, true, true, true, true, false, 8.0D, 1301L),
			1301L, 200L, 48.0D));

		tracker.record(MEMBER, 1400L);
		tracker.clear();
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(tracker, MEMBER, true, true, true, true, false, 8.0D, 1400L),
			1400L, 200L, 48.0D));
	}

	private static CircleContributionPolicy.MemberContext context(CircleContributionTracker tracker,
			UUID member, boolean sameCircle, boolean online, boolean sameDimension, boolean alive,
			boolean spectator, double distanceBlocks, long nowTick) {
		return new CircleContributionPolicy.MemberContext(member, sameCircle, online, sameDimension,
			alive, spectator, distanceBlocks * distanceBlocks, tracker.lastContributionTick(member));
	}
}
