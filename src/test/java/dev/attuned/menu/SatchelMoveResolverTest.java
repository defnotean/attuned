package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SatchelMoveResolverTest {
	@Test
	void satchelToEquippedOnEmptyTargetClearsSatchelSlot() {
		List<Optional<String>> satchel = List.of(Optional.of("attuned:edge_focus"), Optional.empty());
		List<Optional<String>> equipped = List.of(Optional.empty(), Optional.empty());
		SatchelMoveResolver.Move move = SatchelMoveResolver.satchelToEquipped(satchel, equipped, 0, 0);
		assertTrue(move.applied(), "A valid satchel->equipped move applies.");
		assertEquals(Optional.of("attuned:edge_focus"), move.equippedWrite(), "Focus equips into the target slot.");
		assertEquals(Optional.empty(), move.satchelWrite(), "Source satchel slot clears when target was empty.");
	}

	@Test
	void satchelToEquippedOnOccupiedTargetSwapsAndPreservesDisplacedFocus() {
		List<Optional<String>> satchel = List.of(Optional.of("attuned:edge_focus"));
		List<Optional<String>> equipped = List.of(Optional.of("attuned:iron_focus"));
		SatchelMoveResolver.Move move = SatchelMoveResolver.satchelToEquipped(satchel, equipped, 0, 0);
		assertEquals(Optional.of("attuned:edge_focus"), move.equippedWrite());
		assertEquals(Optional.of("attuned:iron_focus"), move.satchelWrite(),
			"The displaced equipped focus must be written back into the satchel slot - never destroyed.");
	}

	@Test
	void satchelToEquippedRejectsEmptySource() {
		List<Optional<String>> satchel = List.of(Optional.empty());
		List<Optional<String>> equipped = List.of(Optional.empty());
		assertTrue(!SatchelMoveResolver.satchelToEquipped(satchel, equipped, 0, 0).applied(),
			"Moving from an empty satchel slot is a no-op.");
	}

	@Test
	void equippedToSatchelFindsFreeSlotAndClearsEquip() {
		List<Optional<String>> satchel = List.of(Optional.of("attuned:edge_focus"), Optional.empty());
		List<Optional<String>> equipped = List.of(Optional.of("attuned:iron_focus"));
		SatchelMoveResolver.Move move = SatchelMoveResolver.equippedToSatchel(satchel, equipped, 0, -1);
		assertTrue(move.applied());
		assertEquals(1, move.satchelSlot(), "Focus goes into the first free satchel slot.");
		assertEquals(Optional.of("attuned:iron_focus"), move.satchelWrite());
		assertEquals(Optional.empty(), move.equippedWrite(), "Equipped slot clears.");
	}

	@Test
	void equippedToSatchelOnFullSatchelReturnsNoMoveSoNothingIsLost() {
		List<Optional<String>> satchel = List.of(Optional.of("a"), Optional.of("b"));
		List<Optional<String>> equipped = List.of(Optional.of("attuned:iron_focus"));
		assertTrue(!SatchelMoveResolver.equippedToSatchel(satchel, equipped, 0, -1).applied(),
			"A full satchel leaves the focus equipped rather than eating it.");
	}
}
