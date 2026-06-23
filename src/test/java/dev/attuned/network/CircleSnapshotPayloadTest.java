package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleSnapshotPayloadTest {
	private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000302");

	@Test
	void snapshotSanitizesCircleAndMemberNames() {
		CircleSnapshotPayload payload = new CircleSnapshotPayload("  Ember\u00A7c Watch\n",
			List.of(new CircleSnapshotPayload.Member(LEADER, "\u00A7aLeader\tName", true,
				"\u00A7bDiscord\n", "\u00A7dShade\r", 3, 1)));

		assertEquals("Ember Watch", payload.name());
		assertEquals("LeaderName", payload.members().get(0).name());
		assertEquals("Discord", payload.members().get(0).stance());
		assertEquals("Shade", payload.members().get(0).role());
		assertEquals(3, payload.members().get(0).activeCount());
		assertEquals(1, payload.members().get(0).dormantCount());
	}

	@Test
	void memberPublicSummaryDefaultsAndClampsCounts() {
		CircleSnapshotPayload.Member member =
			new CircleSnapshotPayload.Member(LEADER, "Leader", true, "\n\t", "\u00A7c\n", -3, -1);

		assertEquals("None", member.stance());
		assertEquals("", member.role());
		assertEquals(0, member.activeCount());
		assertEquals(0, member.dormantCount());
	}

	@Test
	void snapshotCopiesMembersAndRejectsNullIds() {
		List<CircleSnapshotPayload.Member> members = new ArrayList<>();
		members.add(new CircleSnapshotPayload.Member(LEADER, "Leader", true,
			"Fury", "Vanguard", 2, 0));
		CircleSnapshotPayload payload = new CircleSnapshotPayload("Circle", members);

		members.add(new CircleSnapshotPayload.Member(SECOND, "Second", false,
			"Neutral", "", 1, 2));

		assertEquals(1, payload.members().size(),
			"Client snapshots should copy the member list so later mutations cannot rewrite cached state.");
		assertThrows(NullPointerException.class,
			() -> new CircleSnapshotPayload.Member(null, "Nobody", false,
				"None", "", 0, 0));
	}

	@Test
	void snapshotClampsPublicMemberRowsToConfiguredMaximum() {
		List<CircleSnapshotPayload.Member> members = new ArrayList<>();
		for (int index = 0; index < 12; index++) {
			members.add(new CircleSnapshotPayload.Member(
				new UUID(0L, 0x400L + index), "Member " + index, index == 0,
				"Neutral", "", 1, 0));
		}

		CircleSnapshotPayload payload = new CircleSnapshotPayload("Circle", members);

		assertEquals(8, payload.members().size(),
			"Public Circle snapshots should never cache or render more rows than the server config allows.");
		assertEquals(new UUID(0L, 0x400L), payload.members().get(0).id());
		assertEquals(new UUID(0L, 0x407L), payload.members().get(7).id());
	}

	@Test
	void emptySnapshotHasNoMembers() {
		assertEquals(List.of(), CircleSnapshotPayload.EMPTY.members());
		assertEquals("", CircleSnapshotPayload.EMPTY.name());
	}
}
