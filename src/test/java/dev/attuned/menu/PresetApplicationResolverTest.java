package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PresetApplicationResolverTest {
	@Test
	void sourcesFromSatchelThenInventoryAndConservesTotalFocusCount() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals("attuned:edge_focus", r.equips().get(0), "Slot 0 equips the sourced focus.");
		assertTrue(!r.satchel().contains("attuned:edge_focus"), "The sourced focus is consumed from the satchel.");
		assertTrue(r.missing().isEmpty(), "Nothing missing when the focus is available.");
	}

	@Test
	void returnsPreviouslyEquippedFociToThePoolSoTheyAreNotDestroyed() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("attuned:iron_focus", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of(),
			java.util.Set.of("attuned:edge_focus", "attuned:iron_focus"));
		long ironInSatchel = r.satchel().stream().filter("attuned:iron_focus"::equals).count();
		assertTrue(ironInSatchel >= 1, "The displaced iron focus is returned to the satchel pool, not deleted.");
	}

	@Test
	void absentItemLeavesSlotEmptyAndRecordsMissing() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of(), Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals("", r.equips().get(0), "An unsourced focus leaves the slot empty.");
		assertTrue(r.missing().contains("attuned:edge_focus"), "An unsourced focus is recorded as missing.");
	}

	@Test
	void unknownOrRemovedFocusIdIsTreatedAsMissingNotEquipped() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:deleted_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:deleted_focus"), Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals("", r.equips().get(0), "An id not in the focus registry is never equipped.");
		assertTrue(r.missing().contains("attuned:deleted_focus"), "Removed/unknown ids are reported missing.");
	}

	@Test
	void duplicateUniqueIdAcrossTwoSlotsEquipsBothLeavingDormancyToTheBudget() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "attuned:edge_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus", "attuned:edge_focus"), Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals("attuned:edge_focus", r.equips().get(0));
		assertEquals("attuned:edge_focus", r.equips().get(1),
			"Two copies both equip (storage); uniqueness/budget dormancy is resolved elsewhere.");
	}

	@Test
	void consumedInventoryReportsItemsUsedNotItemsRemaining() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "attuned:edge_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of(), Map.of("attuned:edge_focus", 3),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals(2, r.consumedInventory().get("attuned:edge_focus"),
			"consumedInventory should report the two inventory Foci used, not the one left over.");
	}

	@Test
	void unregisteredSourceIdsStayInStorageWhenDefinitionsAreMissing() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("", "", "", "", "", ""),
			List.of("attuned:deleted_focus", "", "", "", "", ""),
			List.of("attuned:deleted_focus", "", "attuned:edge_focus"), Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		long preservedUnknownCopies = r.satchel().stream().filter("attuned:deleted_focus"::equals).count();
		assertEquals(2, preservedUnknownCopies,
			"Definitionless Foci should stay in storage instead of being silently deleted.");
		assertTrue(r.satchel().contains("attuned:edge_focus"),
			"Registered source ids should remain available in the satchel residual.");
	}

	@Test
	void preservesUnconsumedSatchelSlotPositionsWhenApplyingPreset() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("", "attuned:iron_focus", "attuned:edge_focus"),
			Map.of(),
			java.util.Set.of("attuned:edge_focus", "attuned:iron_focus"));
		assertEquals(List.of("", "attuned:iron_focus", ""),
			r.satchel(),
			"Applying a preset should clear consumed satchel slots without compacting the player's layout.");
	}
}
