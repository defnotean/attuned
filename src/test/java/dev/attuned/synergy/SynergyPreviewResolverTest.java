package dev.attuned.synergy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Minecraft-free behavioral coverage for the "one member away" Confluence preview policy. */
class SynergyPreviewResolverTest {

	private static final SynergyResolver.SynergyDef HUNTERS_PATIENCE =
		new SynergyResolver.SynergyDef("attuned:hunters_patience",
			List.of("attuned:lantern_focus", "attuned:veil_focus"));
	private static final SynergyResolver.SynergyDef CARTOGRAPHERS_TRUST =
		new SynergyResolver.SynergyDef("attuned:cartographers_trust",
			List.of("attuned:beacon_focus", "attuned:waystone_focus", "attuned:driftglass_focus"));

	@Test
	void oneMemberAwayFromDiscoveredConfluenceIsPreviewed() {
		Optional<String> result = SynergyResolver.previewOf(
			Set.of("attuned:lantern_focus"),
			Set.of("attuned:hunters_patience"),
			List.of(HUNTERS_PATIENCE));
		assertEquals(Optional.of("attuned:hunters_patience"), result,
			"A discovered confluence one active member short should be previewed.");
	}

	@Test
	void undiscoveredConfluenceIsNeverPreviewed() {
		Optional<String> result = SynergyResolver.previewOf(
			Set.of("attuned:lantern_focus"),
			Set.of(),
			List.of(HUNTERS_PATIENCE));
		assertTrue(result.isEmpty(),
			"An undiscovered confluence must never be hinted, so the meta is not spoiled.");
	}

	@Test
	void fullyActiveConfluenceIsNotPreviewed() {
		Optional<String> result = SynergyResolver.previewOf(
			Set.of("attuned:lantern_focus", "attuned:veil_focus"),
			Set.of("attuned:hunters_patience"),
			List.of(HUNTERS_PATIENCE));
		assertTrue(result.isEmpty(), "An already-active confluence is not a 'needs one more' hint.");
	}

	@Test
	void twoOrMoreMembersAwayIsNotPreviewed() {
		Optional<String> result = SynergyResolver.previewOf(
			Set.of(),
			Set.of("attuned:hunters_patience"),
			List.of(HUNTERS_PATIENCE));
		assertTrue(result.isEmpty(), "A confluence two members short is too far to hint.");
	}

	@Test
	void threeMemberConfluenceOneAwayIsPreviewed() {
		Optional<String> result = SynergyResolver.previewOf(
			Set.of("attuned:beacon_focus", "attuned:waystone_focus"),
			Set.of("attuned:cartographers_trust"),
			List.of(CARTOGRAPHERS_TRUST));
		assertEquals(Optional.of("attuned:cartographers_trust"), result,
			"A discovered triad one active member short should be previewed.");
	}

	@Test
	void ambiguousOneAwayPairIsNotPreviewed() {
		SynergyResolver.SynergyDef other =
			new SynergyResolver.SynergyDef("attuned:other",
				List.of("attuned:lantern_focus", "attuned:smoke_focus"));
		Optional<String> result = SynergyResolver.previewOf(
			Set.of("attuned:lantern_focus"),
			Set.of("attuned:hunters_patience", "attuned:other"),
			List.of(HUNTERS_PATIENCE, other));
		assertTrue(result.isEmpty(),
			"When two discovered confluences are each one-away, the hint is ambiguous and suppressed.");
	}
}
