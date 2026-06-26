package dev.attuned.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AttunedPlayerStateKeyContractTest {
	@Test
	void playerStateKeysUseStableAttunedIds() {
		assertEquals("attuned:capacity", AttunedPlayerStateKey.CAPACITY.id().toString());
		assertEquals("attuned:inventory", AttunedPlayerStateKey.INVENTORY.id().toString());
		assertEquals("attuned:presets", AttunedPlayerStateKey.PRESETS.id().toString());
		assertEquals("attuned:milestones", AttunedPlayerStateKey.MILESTONES.id().toString());
		assertEquals("attuned:resonance", AttunedPlayerStateKey.RESONANCE.id().toString());
		assertEquals("attuned:onboarding", AttunedPlayerStateKey.ONBOARDING.id().toString());
		assertEquals("attuned:pact_trial_progress", AttunedPlayerStateKey.PACT_TRIAL_PROGRESS.id().toString());
		assertEquals("attuned:discovered_confluences", AttunedPlayerStateKey.DISCOVERED_CONFLUENCES.id().toString());
	}

	@Test
	void playerStateKeysDeclareLoaderPortPolicies() {
		for (AttunedPlayerStateKey key : AttunedPlayerStateKey.values()) {
			assertTrue(key.persistent(), key + " must persist across restarts");
			assertTrue(key.copyOnDeath(), key + " must copy on death");
		}

		assertTrue(AttunedPlayerStateKey.CAPACITY.syncedToOwner());
		assertTrue(AttunedPlayerStateKey.INVENTORY.syncedToOwner());
		assertTrue(AttunedPlayerStateKey.PRESETS.syncedToOwner());
		assertFalse(AttunedPlayerStateKey.MILESTONES.syncedToOwner());
		assertTrue(AttunedPlayerStateKey.RESONANCE.syncedToOwner());
		assertFalse(AttunedPlayerStateKey.ONBOARDING.syncedToOwner());
		assertTrue(AttunedPlayerStateKey.PACT_TRIAL_PROGRESS.syncedToOwner());
		assertTrue(AttunedPlayerStateKey.DISCOVERED_CONFLUENCES.syncedToOwner());
	}
}
