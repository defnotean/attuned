package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.menu.BuildPreviewResolver.Availability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Behavioral, Minecraft-free coverage for the build preview availability resolver.
 * The sourcing rule must match {@link PresetApplicationResolver}: a build slot is
 * OWNED only if a copy of its id can be drawn from the multiset pooled across
 * equipped, satchel, and loose inventory Foci. Two slots wanting the same id need
 * two copies. Empty-string slots are always OWNED.
 */
class BuildPreviewResolverTest {
	@Test
	void bothSlotsMissingWhenNothingIsAvailable() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:edge_focus", "attuned:iron_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of(),
			Map.of());
		assertEquals(Availability.MISSING, result.get(0), "Unsourced focus is MISSING.");
		assertEquals(Availability.MISSING, result.get(1), "Second unsourced focus is MISSING.");
		assertEquals(Availability.OWNED, result.get(2), "Empty slots are OWNED.");
	}

	@Test
	void duplicateIdWithOneCopyMarksEveryRequestedSlotMissingBecauseApplyFailsClosed() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:edge_focus", "attuned:edge_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of());
		assertEquals(Availability.MISSING, result.get(0), "All-or-nothing Apply will reject the whole duplicate build.");
		assertEquals(Availability.MISSING, result.get(1), "A single copy cannot fill two slots.");
	}

	@Test
	void replacementPreviewIsMissingWhenTheDisplacedFocusHasNoSafeStorage() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("attuned:iron_focus", "", "", "", "", ""),
			List.of("attuned:filled_a", "attuned:filled_b"),
			Map.of("attuned:edge_focus", 1));

		assertEquals(Availability.MISSING, result.get(0),
			"Preview must mirror Apply: replacing iron with a full reliquary would strand iron, so the build cannot apply.");
	}

	@Test
	void poolsAcrossEquippedSatchelAndInventory() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:edge_focus", "attuned:edge_focus", "attuned:edge_focus", "", "", ""),
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of("attuned:edge_focus", 1));
		assertEquals(Availability.OWNED, result.get(0), "Sourced from equipped.");
		assertEquals(Availability.OWNED, result.get(1), "Sourced from satchel.");
		assertEquals(Availability.OWNED, result.get(2), "Sourced from inventory.");
	}

	@Test
	void inventoryAndSatchelPoolingFillsBothSlots() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:edge_focus", "attuned:edge_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of("attuned:edge_focus", 1));
		assertEquals(Availability.OWNED, result.get(0), "First slot from satchel.");
		assertEquals(Availability.OWNED, result.get(1), "Second slot from inventory.");
	}

	@Test
	void emptySlotIsAlwaysOwned() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of(),
			Map.of());
		for (Availability availability : result) {
			assertEquals(Availability.OWNED, availability, "Every empty slot is OWNED.");
		}
		assertEquals(6, result.size(), "There is one availability per build slot.");
	}

	@Test
	void inventoryCountShortfallMarksEveryRequestedSlotMissingBecauseApplyFailsClosed() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:edge_focus", "attuned:edge_focus", "attuned:edge_focus", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of(),
			Map.of("attuned:edge_focus", 2));
		assertEquals(Availability.MISSING, result.get(0), "The whole build cannot apply when any requested copy is missing.");
		assertEquals(Availability.MISSING, result.get(1), "The whole build cannot apply when any requested copy is missing.");
		assertEquals(Availability.MISSING, result.get(2), "Only two copies exist.");
	}

	@Test
	void removedFocusDefinitionStaysMissingEvenWhenAStoredStackHasTheSameItemId() {
		List<Availability> result = BuildPreviewResolver.availability(
			List.of("attuned:deleted_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:deleted_focus"),
			Map.of(),
			Set.of("attuned:edge_focus"));

		assertEquals(Availability.MISSING, result.get(0),
			"Preview must mirror Apply: a removed FocusDefinition cannot be sourced just because a stale stack exists.");
	}
}
