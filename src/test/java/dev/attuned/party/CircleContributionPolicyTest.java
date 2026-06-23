package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleContributionPolicyTest {
	private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000011");

	@Test
	void nearbyAliveContributorInSameDimensionIsEligibleWithinWindow() {
		CircleContributionPolicy.MemberContext member = context(true, true, false, 32.0D, 900L);

		assertTrue(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void idleMemberOutsideContributionWindowIsNotEligible() {
		CircleContributionPolicy.MemberContext member = context(true, true, false, 32.0D, 700L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void contributionWindowIncludesExactBoundaryTick() {
		CircleContributionPolicy.MemberContext member = context(true, true, false, 32.0D, 800L);

		assertTrue(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void crossDimensionMemberIsNeverEligible() {
		CircleContributionPolicy.MemberContext member = context(false, true, false, 12.0D, 990L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void crossDimensionMemberCanOnlyQualifyWhenExplicitlyAllowed() {
		CircleContributionPolicy.MemberContext member = context(false, true, false, 12.0D, 990L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D, false),
			"The explicit overload should preserve the default same-dimension requirement when disabled.");
		assertTrue(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D, true),
			"Server config opt-in should allow cross-dimension shared credit without bypassing activity checks.");
	}

	@Test
	void crossDimensionOptInStillRequiresRecentNearbyOnlineContributors() {
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(false, true, false, 49.0D, 990L), 1000L, 200L, 48.0D, true),
			"Cross-dimension opt-in should not bypass the configured credit radius.");
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(false, true, false, 12.0D, 700L), 1000L, 200L, 48.0D, true),
			"Cross-dimension opt-in should not bypass the recent contribution window.");
	}

	@Test
	void differentCircleMemberIsNotEligibleEvenWhenNearbyAndActive() {
		CircleContributionPolicy.MemberContext member =
			new CircleContributionPolicy.MemberContext(MEMBER, false, true, true, true, false, 12.0D * 12.0D, 990L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void offlineMemberIsNotEligibleEvenWithRecentContribution() {
		CircleContributionPolicy.MemberContext member =
			new CircleContributionPolicy.MemberContext(MEMBER, true, false, true, true, false, 12.0D * 12.0D, 990L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void deadOrSpectatingMemberIsNeverEligible() {
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(true, false, false, 12.0D, 990L), 1000L, 200L, 48.0D));
		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(
			context(true, true, true, 12.0D, 990L), 1000L, 200L, 48.0D));
	}

	@Test
	void distantMemberIsNotEligibleEvenWithRecentContribution() {
		CircleContributionPolicy.MemberContext member = context(true, true, false, 49.0D, 990L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void futureContributionTicksAreRejectedInsteadOfExtendingCredit() {
		CircleContributionPolicy.MemberContext member = context(true, true, false, 12.0D, 1100L);

		assertFalse(CircleContributionPolicy.eligibleForSharedCredit(member, 1000L, 200L, 48.0D));
	}

	@Test
	void eligibleMembersReturnsOnlySameCircleOnlineNearbyContributors() {
		UUID eligible = UUID.fromString("00000000-0000-0000-0000-000000000012");
		UUID outsider = UUID.fromString("00000000-0000-0000-0000-000000000013");
		UUID offline = UUID.fromString("00000000-0000-0000-0000-000000000014");
		UUID idle = UUID.fromString("00000000-0000-0000-0000-000000000015");
		List<CircleContributionPolicy.MemberContext> candidates = List.of(
			new CircleContributionPolicy.MemberContext(eligible, true, true, true, true, false,
				16.0D * 16.0D, 990L),
			new CircleContributionPolicy.MemberContext(outsider, false, true, true, true, false,
				16.0D * 16.0D, 990L),
			new CircleContributionPolicy.MemberContext(offline, true, false, true, true, false,
				16.0D * 16.0D, 990L),
			new CircleContributionPolicy.MemberContext(idle, true, true, true, true, false,
				16.0D * 16.0D, 700L));

		List<UUID> eligibleMembers = CircleContributionPolicy.eligibleMembers(candidates, 1000L, 200L, 48.0D)
			.stream()
			.map(CircleContributionPolicy.MemberContext::member)
			.toList();

		assertEquals(List.of(eligible), eligibleMembers);
	}

	@Test
	void eligibleMembersCanUseCrossDimensionOptIn() {
		UUID local = UUID.fromString("00000000-0000-0000-0000-000000000016");
		UUID remote = UUID.fromString("00000000-0000-0000-0000-000000000017");
		List<CircleContributionPolicy.MemberContext> candidates = List.of(
			new CircleContributionPolicy.MemberContext(local, true, true, true, true, false,
				16.0D * 16.0D, 990L),
			new CircleContributionPolicy.MemberContext(remote, true, true, false, true, false,
				16.0D * 16.0D, 990L));

		List<UUID> defaultEligible = CircleContributionPolicy.eligibleMembers(candidates, 1000L, 200L, 48.0D)
			.stream()
			.map(CircleContributionPolicy.MemberContext::member)
			.toList();
		List<UUID> crossDimensionEligible =
			CircleContributionPolicy.eligibleMembers(candidates, 1000L, 200L, 48.0D, true)
				.stream()
				.map(CircleContributionPolicy.MemberContext::member)
				.toList();

		assertEquals(List.of(local), defaultEligible);
		assertEquals(List.of(local, remote), crossDimensionEligible);
	}

	private static CircleContributionPolicy.MemberContext context(boolean sameDimension, boolean alive,
			boolean spectator, double distanceBlocks, long lastContributionTick) {
		return new CircleContributionPolicy.MemberContext(MEMBER, true, true, sameDimension, alive, spectator,
			distanceBlocks * distanceBlocks, lastContributionTick);
	}
}
