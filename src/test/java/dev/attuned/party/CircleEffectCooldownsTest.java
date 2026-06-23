package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleEffectCooldownsTest {
	private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000401");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000402");
	private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000403");
	private static final String EFFECT = "attuned:rescue_shield";

	@Test
	void cooldownIsKeyedByStableCircleAndEffectNotTheCurrentMemberList() {
		CircleEffectCooldowns cooldowns = new CircleEffectCooldowns();
		CirclePolicy.Circle original = circle("circle-a", LEADER, SECOND);
		CirclePolicy.Circle afterRejoin = circle("circle-a", SECOND, LEADER);

		cooldowns.start(original, EFFECT, 100L, 200L);

		assertFalse(cooldowns.ready(afterRejoin, EFFECT, 150L),
			"Leaving and rejoining the same Circle must not reset a party-wide effect cooldown.");
		assertEquals(150L, cooldowns.remaining(afterRejoin, EFFECT, 150L));
		assertTrue(cooldowns.ready(afterRejoin, "attuned:other_effect", 150L),
			"Different party effects should not share cooldown state.");
	}

	@Test
	void memberTombstonesBlockPartyHoppingUntilTheEffectCooldownExpires() {
		CircleEffectCooldowns cooldowns = new CircleEffectCooldowns();
		CirclePolicy.Circle original = circle("circle-a", LEADER, SECOND);
		CirclePolicy.Circle hopped = circle("circle-b", SECOND, THIRD);
		CirclePolicy.Circle unrelated = circle("circle-c", THIRD);

		cooldowns.start(original, EFFECT, 100L, 200L);

		assertEquals(120L, cooldowns.remaining(hopped, EFFECT, 180L),
			"A member who just benefited from a Circle effect should carry that cooldown into another Circle.");
		assertEquals(0L, cooldowns.remaining(unrelated, EFFECT, 180L),
			"Uninvolved members should not inherit another Circle's effect cooldown.");
	}

	@Test
	void disbandCopiesActiveCircleCooldownsToMemberTombstonesForReforms() {
		CircleEffectCooldowns cooldowns = new CircleEffectCooldowns();
		CirclePolicy.Circle original = circle("circle-a", LEADER, SECOND);
		CirclePolicy.Circle reformed = circle("circle-b", LEADER, SECOND);

		cooldowns.start(original, EFFECT, 100L, 200L);
		cooldowns.forgetCircle(original, 120L, 30L);

		assertEquals(150L, cooldowns.remaining(reformed, EFFECT, 150L),
			"Disbanding and reforming under a new Circle id should not clear an active group-effect cooldown.");
		assertTrue(cooldowns.ready(reformed, EFFECT, 300L),
			"The tombstone should expire once the original effect cooldown has elapsed.");
	}

	@Test
	void pruneAndClearDropExpiredCooldownState() {
		CircleEffectCooldowns cooldowns = new CircleEffectCooldowns();
		CirclePolicy.Circle circle = circle("circle-a", LEADER, SECOND);

		cooldowns.start(circle, EFFECT, 100L, 20L);
		cooldowns.prune(121L);

		assertTrue(cooldowns.ready(circle, EFFECT, 121L));

		cooldowns.start(circle, EFFECT, 200L, 200L);
		cooldowns.clear();

		assertTrue(cooldowns.ready(circle, EFFECT, 250L));
	}

	private static CirclePolicy.Circle circle(String id, UUID leader, UUID... members) {
		List<UUID> allMembers = new ArrayList<>();
		allMembers.add(leader);
		for (UUID member : members) {
			if (!allMembers.contains(member)) {
				allMembers.add(member);
			}
		}
		return new CirclePolicy.Circle(id, leader, allMembers, id, Map.of());
	}
}
