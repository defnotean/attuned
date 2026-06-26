package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.attunement.FocusPreset;
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

	@Test
	void allOrNothingApplyPreservesStateWhenAnyRequestedFocusIsInvalid() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.applyAllOrNothing(
			List.of("attuned:edge_focus", "attuned:deleted_focus", "", "", "", ""),
			List.of("attuned:iron_focus", "", "", "", "", ""),
			List.of("", "attuned:edge_focus", "attuned:verdant_focus"),
			Map.of("attuned:edge_focus", 1),
			java.util.Set.of("attuned:edge_focus", "attuned:iron_focus", "attuned:verdant_focus"));

		assertEquals(List.of("attuned:iron_focus", "", "", "", "", ""), r.equips(),
			"Fail-closed apply should preserve currently equipped slots when any target is invalid.");
		assertEquals(List.of("", "attuned:edge_focus", "attuned:verdant_focus"), r.satchel(),
			"Fail-closed apply should not consume or move satchel contents on an invalid target.");
		assertTrue(r.consumedInventory().isEmpty(),
			"Fail-closed apply should not consume loose inventory when the build cannot fully apply.");
		assertEquals(List.of("attuned:deleted_focus"), r.missing(),
			"The missing/invalid id should still be reported for player feedback.");
	}

	@Test
	void allOrNothingApplyRejectsDuplicateRequestWhenOnlyOneCopyExists() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.applyAllOrNothing(
			List.of("attuned:edge_focus", "attuned:edge_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of(),
			java.util.Set.of("attuned:edge_focus"));

		assertEquals(List.of("", "", "", "", "", ""), r.equips(),
			"A duplicate request with one physical copy must not partially equip the first slot.");
		assertEquals(List.of("attuned:edge_focus"), r.satchel(),
			"The single physical copy should remain where it started when the full preset cannot apply.");
		assertTrue(r.consumedInventory().isEmpty());
		assertEquals(List.of("attuned:edge_focus"), r.missing(),
			"The second duplicate request should be reported as unsourced.");
	}

	@Test
	void allOrNothingApplyRejectsReplacementWhenDisplacedFocusCannotBeStored() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.applyAllOrNothing(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("attuned:iron_focus", "", "", "", "", ""),
			List.of("attuned:filled_a", "attuned:filled_b"),
			Map.of("attuned:edge_focus", 1),
			java.util.Set.of("attuned:edge_focus", "attuned:iron_focus", "attuned:filled_a", "attuned:filled_b"));

		assertEquals(List.of("attuned:iron_focus", "", "", "", "", ""), r.equips(),
			"Fail-closed apply should keep equipped Foci unchanged when the displaced Focus has no safe storage.");
		assertEquals(List.of("attuned:filled_a", "attuned:filled_b"), r.satchel(),
			"Fail-closed apply should not mutate a full satchel when replacement would strand a displaced Focus.");
		assertTrue(r.consumedInventory().isEmpty());
		assertEquals(List.of("attuned:iron_focus"), r.missing(),
			"The displaced Focus id should be reported as the reason the full apply was rejected.");
	}

	@Test
	void allOrNothingApplyStillAppliesWhenEveryRequestedFocusIsSourced() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.applyAllOrNothing(
			List.of("attuned:edge_focus", "attuned:iron_focus", "", "", "", ""),
			List.of("attuned:iron_focus", "", "", "", "", ""),
			List.of("", "attuned:edge_focus", ""),
			Map.of(),
			java.util.Set.of("attuned:edge_focus", "attuned:iron_focus"));

		assertEquals(List.of("attuned:edge_focus", "attuned:iron_focus", "", "", "", ""), r.equips());
		assertEquals(List.of("", "", ""), r.satchel(),
			"Both requested Foci were sourced without leaving stale copies in the satchel.");
		assertTrue(r.missing().isEmpty());
	}

	@Test
	void temperedPresetSlotConsumesOnlyATemperedSource() {
		String temperedEdge = FocusPreset.slotKey("attuned:edge_focus", true);
		PresetApplicationResolver.Result r = PresetApplicationResolver.applyAllOrNothing(
			List.of(temperedEdge, "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of(temperedEdge, 1),
			java.util.Set.of("attuned:edge_focus"));

		assertEquals(temperedEdge, r.equips().get(0));
		assertEquals(List.of("attuned:edge_focus"), r.satchel(),
			"The untempered copy should remain in storage when the preset asks for a tempered copy.");
		assertEquals(1, r.consumedInventory().get(temperedEdge),
			"The consumed inventory report should identify the exact tempered variant.");
		assertTrue(r.missing().isEmpty());
	}

	@Test
	void temperedPresetSlotFailsClosedWhenOnlyUntemperedCopyExists() {
		String temperedEdge = FocusPreset.slotKey("attuned:edge_focus", true);
		PresetApplicationResolver.Result r = PresetApplicationResolver.applyAllOrNothing(
			List.of(temperedEdge, "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of(),
			java.util.Set.of("attuned:edge_focus"));

		assertEquals(List.of("", "", "", "", "", ""), r.equips());
		assertEquals(List.of("attuned:edge_focus"), r.satchel(),
			"Fail-closed apply should not consume the untempered copy for a tempered slot.");
		assertTrue(r.consumedInventory().isEmpty());
		assertEquals(List.of(temperedEdge), r.missing());
	}

	@Test
	void sequentialAllOrNothingApplyProducesOnlyOneMutationPlanForSameTickDuplicates() {
		List<PresetApplicationResolver.SequentialResult> plans =
			PresetApplicationResolver.applyAllOrNothingSequential(
				List.of(
					List.of("attuned:edge_focus", "", "", "", "", ""),
					List.of("attuned:edge_focus", "", "", "", "", "")),
				List.of("", "", "", "", "", ""),
				List.of("attuned:edge_focus"),
				Map.of(),
				java.util.Set.of("attuned:edge_focus"));

		assertEquals(2, plans.size());
		assertTrue(plans.get(0).applied(),
			"The first request should consume the one physical Focus and mutate the loadout.");
		assertTrue(!plans.get(1).applied(),
			"The duplicate same-tick request should see the post-apply state and produce no second mutation plan.");
		assertEquals("attuned:edge_focus", plans.get(0).result().equips().get(0));
		assertEquals("attuned:edge_focus", plans.get(1).result().equips().get(0),
			"The duplicate request is idempotent against the already-applied state.");
		assertEquals(List.of(""), plans.get(1).result().satchel(),
			"The second plan must not re-source the Focus from the original satchel snapshot.");
		assertTrue(plans.get(1).result().consumedInventory().isEmpty(),
			"The second plan must not consume loose inventory to paper over the duplicate request.");
	}
}
