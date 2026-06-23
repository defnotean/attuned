package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleManagerTest {
	private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000103");
	private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000199");

	@Test
	void createOwnsMembershipAndRejectsSecondCircleForSamePlayer() {
		CircleManager manager = new CircleManager(4, 60L);

		CirclePolicy.Result created = manager.create(LEADER, "Ember Watch");
		CirclePolicy.Result duplicate = manager.create(LEADER, "Second Circle");

		assertEquals(CirclePolicy.Status.OK, created.status());
		assertEquals(CirclePolicy.Status.ALREADY_MEMBER, duplicate.status());
		assertEquals(1, manager.circleCount());
		CirclePolicy.Circle circle = manager.circleOf(LEADER).orElseThrow();
		assertEquals(LEADER, circle.leader());
		assertEquals("Ember Watch", circle.name());
		assertEquals(circle, duplicate.circle().orElseThrow());
	}

	@Test
	void inviteAndAcceptUseCurrentServerOwnedState() {
		CircleManager manager = new CircleManager(4, 60L);
		manager.create(LEADER, "Ember Watch");

		CirclePolicy.Result invited = manager.invite(LEADER, SECOND, 100L);
		CirclePolicy.Result accepted = manager.accept(SECOND, LEADER, 159L);
		CirclePolicy.Result forged = manager.accept(THIRD, LEADER, 160L);

		assertEquals(CirclePolicy.Status.OK, invited.status());
		assertEquals(CirclePolicy.Status.OK, accepted.status());
		assertTrue(manager.circleOf(SECOND).orElseThrow().members().contains(SECOND));
		assertEquals(CirclePolicy.Status.NO_INVITE, forged.status());
		assertTrue(manager.circleOf(THIRD).isEmpty(), "No invite means no membership is created.");
	}

	@Test
	void acceptingInviteFailsWhenTargetAlreadyBelongsToAnotherCircle() {
		CircleManager manager = new CircleManager(4, 60L);
		manager.create(LEADER, "Ember Watch");
		manager.create(SECOND, "Second Circle");
		manager.invite(LEADER, SECOND, 100L);

		CirclePolicy.Result accepted = manager.accept(SECOND, LEADER, 101L);

		assertEquals(CirclePolicy.Status.ALREADY_MEMBER, accepted.status());
		assertEquals(SECOND, manager.circleOf(SECOND).orElseThrow().leader(),
			"Accept cannot move a member out of their current Circle.");
		assertFalse(manager.circleOf(LEADER).orElseThrow().members().contains(SECOND));
	}

	@Test
	void expiredInviteAcceptPrunesServerOwnedInviteState() {
		CircleManager manager = new CircleManager(4, 60L);
		manager.create(LEADER, "Ember Watch");
		manager.invite(LEADER, SECOND, 100L);

		CirclePolicy.Result expired = manager.accept(SECOND, LEADER, 160L);

		assertEquals(CirclePolicy.Status.INVITE_EXPIRED, expired.status());
		assertTrue(manager.circleOf(SECOND).isEmpty());
		assertFalse(manager.circleOf(LEADER).orElseThrow().invites().containsKey(SECOND),
			"Manager state should keep CirclePolicy's expired-invite pruning.");
	}

	@Test
	void senderInviteCooldownStopsRapidInviteFloods() {
		CircleManager manager = new CircleManager(4, 60L, 20L);
		manager.create(LEADER, "Ember Watch");

		CirclePolicy.Result first = manager.invite(LEADER, SECOND, 100L);
		CirclePolicy.Result repeated = manager.invite(LEADER, THIRD, 110L);

		assertEquals(CirclePolicy.Status.OK, first.status());
		assertEquals(CirclePolicy.Status.INVITE_COOLDOWN, repeated.status());
		assertFalse(manager.circleOf(LEADER).orElseThrow().invites().containsKey(THIRD),
			"Cooldown-blocked invites should not mutate pending invite state.");
		CirclePolicy.Result afterCooldown = manager.invite(LEADER, THIRD, 120L);
		assertEquals(CirclePolicy.Status.OK, afterCooldown.status());
		assertTrue(manager.circleOf(LEADER).orElseThrow().invites().containsKey(THIRD));
	}

	@Test
	void recipientInviteCooldownStopsMultipleLeadersFloodingOneTarget() {
		CircleManager manager = new CircleManager(4, 60L, 20L);
		manager.create(LEADER, "Ember Watch");
		manager.create(THIRD, "Third Circle");

		CirclePolicy.Result first = manager.invite(LEADER, SECOND, 100L);
		CirclePolicy.Result flooded = manager.invite(THIRD, SECOND, 110L);

		assertEquals(CirclePolicy.Status.OK, first.status());
		assertEquals(CirclePolicy.Status.INVITE_COOLDOWN, flooded.status());
		assertFalse(manager.circleOf(THIRD).orElseThrow().invites().containsKey(SECOND),
			"Recipient cooldown should block another leader from adding a second pending invite.");
		CirclePolicy.Result afterCooldown = manager.invite(THIRD, SECOND, 120L);
		assertEquals(CirclePolicy.Status.OK, afterCooldown.status());
		assertTrue(manager.circleOf(THIRD).orElseThrow().invites().containsKey(SECOND));
	}

	@Test
	void creationCooldownStopsImmediateDisbandReformLoops() {
		CircleManager manager = new CircleManager(4, 60L, 0L, 30L);

		CirclePolicy.Result created = manager.create(LEADER, "Ember Watch", 100L);
		CirclePolicy.Result disbanded = manager.leave(LEADER, 105L);
		CirclePolicy.Result repeated = manager.create(LEADER, "Second Watch", 110L);

		assertEquals(CirclePolicy.Status.OK, created.status());
		assertEquals(CirclePolicy.Status.DISBANDED, disbanded.status());
		assertEquals(CirclePolicy.Status.CREATE_COOLDOWN, repeated.status());
		assertTrue(manager.circleOf(LEADER).isEmpty(),
			"Cooldown-blocked Circle creation should not recreate membership state.");

		CirclePolicy.Result afterCooldown = manager.create(LEADER, "Second Watch", 135L);
		assertEquals(CirclePolicy.Status.OK, afterCooldown.status());
		assertEquals("Second Watch", manager.circleOf(LEADER).orElseThrow().name());
	}

	@Test
	void timeUnawareCreateCannotBypassCreationCooldown() {
		CircleManager manager = new CircleManager(4, 60L, 0L, 30L);
		manager.create(LEADER, "Ember Watch", 100L);
		manager.leave(LEADER, 105L);

		CirclePolicy.Result repeated = manager.create(LEADER, "Bypass Watch");

		assertEquals(CirclePolicy.Status.CREATE_COOLDOWN, repeated.status());
		assertTrue(manager.circleOf(LEADER).isEmpty());
	}

	@Test
	void creationCooldownIsOnlyMarkedWhenTheCircleActuallyDisbands() {
		CircleManager manager = new CircleManager(4, 60L, 0L, 30L);
		manager.create(LEADER, "Ember Watch", 100L);
		manager.invite(LEADER, SECOND, 101L);
		manager.accept(SECOND, LEADER, 102L);

		CirclePolicy.Result leaderLeft = manager.leave(LEADER, 105L);
		CirclePolicy.Result newCircle = manager.create(LEADER, "Solo Watch", 106L);

		assertEquals(CirclePolicy.Status.OK, leaderLeft.status());
		assertEquals(CirclePolicy.Status.OK, newCircle.status(),
			"Leaving a surviving Circle should not count as a disband/reform cooldown.");
		assertTrue(manager.circleOf(SECOND).isPresent(), "The original Circle should still exist for the survivor.");
		assertTrue(manager.circleOf(LEADER).isPresent(), "The old leader should be able to form a new Circle.");
	}

	@Test
	void leaderDisbandRemovesEveryMemberAndStartsLeaderCreateCooldown() {
		CircleManager manager = new CircleManager(4, 60L, 0L, 30L);
		manager.create(LEADER, "Ember Watch", 100L);
		manager.invite(LEADER, SECOND, 101L);
		manager.accept(SECOND, LEADER, 102L);

		CirclePolicy.Result disbanded = manager.disband(LEADER, 105L);
		CirclePolicy.Result repeated = manager.create(LEADER, "Second Watch", 110L);

		assertEquals(CirclePolicy.Status.DISBANDED, disbanded.status());
		assertEquals(0, manager.circleCount());
		assertTrue(manager.circleOf(LEADER).isEmpty());
		assertTrue(manager.circleOf(SECOND).isEmpty());
		assertEquals(CirclePolicy.Status.CREATE_COOLDOWN, repeated.status());

		CirclePolicy.Result afterCooldown = manager.create(LEADER, "Second Watch", 135L);
		assertEquals(CirclePolicy.Status.OK, afterCooldown.status());
		assertEquals("Second Watch", manager.circleOf(LEADER).orElseThrow().name());
	}

	@Test
	void nonLeaderCannotDisbandCircle() {
		CircleManager manager = populatedManager();

		CirclePolicy.Result denied = manager.disband(SECOND, 200L);

		assertEquals(CirclePolicy.Status.NOT_LEADER, denied.status());
		assertEquals(1, manager.circleCount());
		assertTrue(manager.circleOf(LEADER).isPresent());
		assertTrue(manager.circleOf(SECOND).isPresent());
	}

	@Test
	void kickAndLeaveUpdateTheMembershipIndex() {
		CircleManager manager = populatedManager();

		CirclePolicy.Result kicked = manager.kick(LEADER, THIRD);
		CirclePolicy.Result left = manager.leave(LEADER);

		assertEquals(CirclePolicy.Status.OK, kicked.status());
		assertTrue(manager.circleOf(THIRD).isEmpty(), "Kicked members leave the membership index.");
		assertEquals(CirclePolicy.Status.OK, left.status());
		CirclePolicy.Circle survivorCircle = manager.circleOf(SECOND).orElseThrow();
		assertEquals(SECOND, survivorCircle.leader(), "Leader leaving transfers leadership.");
		assertTrue(manager.circleOf(LEADER).isEmpty(), "Leaving removes the old leader from the membership index.");
	}

	@Test
	void disconnectRemovesMembersAndPrunesPendingInvites() {
		CircleManager manager = new CircleManager(4, 60L);
		manager.create(LEADER, "Ember Watch");
		manager.invite(LEADER, SECOND, 100L);
		manager.invite(LEADER, THIRD, 100L);
		manager.accept(SECOND, LEADER, 101L);

		manager.forgetPlayer(SECOND);
		manager.forgetPlayer(THIRD);

		CirclePolicy.Circle circle = manager.circleOf(LEADER).orElseThrow();
		assertFalse(circle.members().contains(SECOND), "Disconnected members leave transient Circles.");
		assertFalse(circle.invites().containsKey(THIRD), "Pending invites to disconnected players are pruned.");
		assertTrue(manager.circleOf(SECOND).isEmpty());
	}

	@Test
	void clearingManagerDropsEveryCircleAndMembershipIndex() {
		CircleManager manager = populatedManager();

		manager.clear();

		assertEquals(0, manager.circleCount());
		assertTrue(manager.circleOf(LEADER).isEmpty());
		assertTrue(manager.circleOf(SECOND).isEmpty());
		assertTrue(manager.circleOf(THIRD).isEmpty());
	}

	@Test
	void nonMembersCannotMutateSomeoneElsesCircle() {
		CircleManager manager = populatedManager();

		assertEquals(CirclePolicy.Status.NOT_LEADER, manager.invite(SECOND, OUTSIDER, 200L).status());
		assertEquals(CirclePolicy.Status.NOT_MEMBER, manager.kick(OUTSIDER, SECOND).status());
		assertEquals(CirclePolicy.Status.NOT_MEMBER, manager.leave(OUTSIDER).status());
		assertTrue(manager.circleOf(OUTSIDER).isEmpty());
	}

	private static CircleManager populatedManager() {
		CircleManager manager = new CircleManager(4, 60L);
		manager.create(LEADER, "Ember Watch");
		manager.invite(LEADER, SECOND, 100L);
		manager.accept(SECOND, LEADER, 101L);
		manager.invite(LEADER, THIRD, 102L);
		manager.accept(THIRD, LEADER, 103L);
		return manager;
	}
}
