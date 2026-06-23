package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CirclePolicyTest {
	private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID FOURTH = UUID.fromString("00000000-0000-0000-0000-000000000004");
	private static final UUID FIFTH = UUID.fromString("00000000-0000-0000-0000-000000000005");

	@Test
	void createCircleSanitizesNameAndAddsOnlyTheLeader() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER,
			"  Ember\n\u00A7cWatch\t  ");

		assertEquals("circle-a", circle.id());
		assertEquals("EmberWatch", circle.name(),
			"Circle names should strip control/formatting characters and trim whitespace.");
		assertEquals(LEADER, circle.leader());
		assertEquals(1, circle.members().size());
		assertTrue(circle.members().contains(LEADER));
		assertTrue(circle.invites().isEmpty(), "A new Circle starts with no pending invites.");
	}

	@Test
	void blankCircleNameFallsBackToCircle() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, " \n\t\u00A7r ");

		assertEquals("Circle", circle.name());
	}

	@Test
	void circleNameStripsMinecraftSectionSignFormattingCodes() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER,
			"  Ember\n\u00A7cWatch\t\u00A7r  ");

		assertEquals("EmberWatch", circle.name(),
			"Circle names should strip the real Minecraft formatting introducer and its format code.");
	}

	@Test
	void circleNameIsCappedForHudAndChatSafety() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER,
			"abcdefghijklmnopqrstuvwxyz0123456789-extra");

		assertEquals(32, circle.name().length());
		assertEquals("abcdefghijklmnopqrstuvwxyz012345", circle.name());
	}

	@Test
	void leaderCanInviteANonMemberWithAnExpirationTick() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, "Ember Watch");

		CirclePolicy.Result result = CirclePolicy.invite(circle, LEADER, SECOND, 100L, 60L, 4);

		assertEquals(CirclePolicy.Status.OK, result.status());
		CirclePolicy.Circle updated = result.circle().orElseThrow();
		assertTrue(updated.invites().containsKey(SECOND));
		assertEquals(LEADER, updated.invites().get(SECOND).sender());
		assertEquals(160L, updated.invites().get(SECOND).expiresAtTick());
		assertEquals(1, updated.members().size(), "Inviting does not add the target until they accept.");
	}

	@Test
	void nonLeaderCannotInvite() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, "Ember Watch");

		CirclePolicy.Result result = CirclePolicy.invite(circle, SECOND, THIRD, 100L, 60L, 4);

		assertEquals(CirclePolicy.Status.NOT_LEADER, result.status());
		assertTrue(result.circle().orElseThrow().invites().isEmpty());
	}

	@Test
	void cannotInviteExistingMember() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, "Ember Watch");

		CirclePolicy.Result result = CirclePolicy.invite(circle, LEADER, LEADER, 100L, 60L, 4);

		assertEquals(CirclePolicy.Status.ALREADY_MEMBER, result.status());
		assertTrue(result.circle().orElseThrow().invites().isEmpty());
	}

	@Test
	void acceptingValidInviteAddsMemberAndConsumesInvite() {
		CirclePolicy.Circle circle = CirclePolicy.invite(
			CirclePolicy.create("circle-a", LEADER, "Ember Watch"), LEADER, SECOND, 100L, 60L, 4)
			.circle().orElseThrow();

		CirclePolicy.Result result = CirclePolicy.accept(circle, SECOND, 159L, 4);

		assertEquals(CirclePolicy.Status.OK, result.status());
		CirclePolicy.Circle updated = result.circle().orElseThrow();
		assertTrue(updated.members().contains(SECOND));
		assertFalse(updated.invites().containsKey(SECOND), "Accepting an invite consumes it.");
	}

	@Test
	void acceptingAtExpirationTickFailsAndRemovesInvite() {
		CirclePolicy.Circle circle = CirclePolicy.invite(
			CirclePolicy.create("circle-a", LEADER, "Ember Watch"), LEADER, SECOND, 100L, 60L, 4)
			.circle().orElseThrow();

		CirclePolicy.Result result = CirclePolicy.accept(circle, SECOND, 160L, 4);

		assertEquals(CirclePolicy.Status.INVITE_EXPIRED, result.status());
		CirclePolicy.Circle updated = result.circle().orElseThrow();
		assertFalse(updated.members().contains(SECOND));
		assertFalse(updated.invites().containsKey(SECOND), "Expired invites should be pruned on accept.");
	}

	@Test
	void acceptingWithoutInviteFails() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, "Ember Watch");

		CirclePolicy.Result result = CirclePolicy.accept(circle, SECOND, 100L, 4);

		assertEquals(CirclePolicy.Status.NO_INVITE, result.status());
		assertEquals(circle, result.circle().orElseThrow());
	}

	@Test
	void partyCapPreventsInvitesAndAcceptsBeyondCapacity() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, "Ember Watch");
		circle = CirclePolicy.invite(circle, LEADER, SECOND, 100L, 60L, 4).circle().orElseThrow();
		circle = CirclePolicy.accept(circle, SECOND, 101L, 4).circle().orElseThrow();
		circle = CirclePolicy.invite(circle, LEADER, THIRD, 102L, 60L, 4).circle().orElseThrow();
		circle = CirclePolicy.accept(circle, THIRD, 103L, 4).circle().orElseThrow();
		circle = CirclePolicy.invite(circle, LEADER, FOURTH, 104L, 60L, 4).circle().orElseThrow();
		circle = CirclePolicy.accept(circle, FOURTH, 105L, 4).circle().orElseThrow();

		CirclePolicy.Result fullInvite = CirclePolicy.invite(circle, LEADER, FIFTH, 106L, 60L, 4);

		assertEquals(CirclePolicy.Status.PARTY_FULL, fullInvite.status());
		assertFalse(fullInvite.circle().orElseThrow().invites().containsKey(FIFTH));
	}

	@Test
	void leaderCanKickAnotherMemberButNonLeaderCannot() {
		CirclePolicy.Circle circle = CirclePolicy.invite(
			CirclePolicy.create("circle-a", LEADER, "Ember Watch"), LEADER, SECOND, 100L, 60L, 4)
			.circle().orElseThrow();
		circle = CirclePolicy.accept(circle, SECOND, 101L, 4).circle().orElseThrow();

		CirclePolicy.Result denied = CirclePolicy.kick(circle, SECOND, LEADER);
		assertEquals(CirclePolicy.Status.NOT_LEADER, denied.status());

		CirclePolicy.Result kicked = CirclePolicy.kick(circle, LEADER, SECOND);
		assertEquals(CirclePolicy.Status.OK, kicked.status());
		assertFalse(kicked.circle().orElseThrow().members().contains(SECOND));
	}

	@Test
	void leaderLeavingTransfersLeadershipToNextMember() {
		CirclePolicy.Circle circle = CirclePolicy.invite(
			CirclePolicy.create("circle-a", LEADER, "Ember Watch"), LEADER, SECOND, 100L, 60L, 4)
			.circle().orElseThrow();
		circle = CirclePolicy.accept(circle, SECOND, 101L, 4).circle().orElseThrow();

		CirclePolicy.Result result = CirclePolicy.leave(circle, LEADER);

		assertEquals(CirclePolicy.Status.OK, result.status());
		CirclePolicy.Circle updated = result.circle().orElseThrow();
		assertEquals(SECOND, updated.leader());
		assertFalse(updated.members().contains(LEADER));
	}

	@Test
	void onlyLeaderCanDisbandCircleExplicitly() {
		CirclePolicy.Circle circle = CirclePolicy.invite(
			CirclePolicy.create("circle-a", LEADER, "Ember Watch"), LEADER, SECOND, 100L, 60L, 4)
			.circle().orElseThrow();
		circle = CirclePolicy.accept(circle, SECOND, 101L, 4).circle().orElseThrow();

		CirclePolicy.Result denied = CirclePolicy.disband(circle, SECOND);
		CirclePolicy.Result disbanded = CirclePolicy.disband(circle, LEADER);

		assertEquals(CirclePolicy.Status.NOT_LEADER, denied.status());
		assertEquals(circle, denied.circle().orElseThrow());
		assertEquals(CirclePolicy.Status.DISBANDED, disbanded.status());
		assertEquals(Optional.empty(), disbanded.circle());
	}

	@Test
	void lastMemberLeavingDisbandsCircle() {
		CirclePolicy.Circle circle = CirclePolicy.create("circle-a", LEADER, "Ember Watch");

		CirclePolicy.Result result = CirclePolicy.leave(circle, LEADER);

		assertEquals(CirclePolicy.Status.DISBANDED, result.status());
		assertEquals(Optional.empty(), result.circle());
	}
}
