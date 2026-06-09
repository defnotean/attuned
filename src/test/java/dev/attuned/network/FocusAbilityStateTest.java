package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusAbilityStateTest {
	private static final Path STATE = Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");

	@Test
	void cooldownMessageSecondsUsesCeilingWithoutOverflow() {
		assertEquals(1, FocusAbilityState.cooldownSecondsForMessage(1));
		assertEquals(1, FocusAbilityState.cooldownSecondsForMessage(20));
		assertEquals(2, FocusAbilityState.cooldownSecondsForMessage(21));
		assertEquals(107374183, FocusAbilityState.cooldownSecondsForMessage(Integer.MAX_VALUE));
	}

	@Test
	void cooldownsAreScopedToTheCurrentlySelectedAbilityFocus() throws IOException {
		String state = Files.readString(STATE, StandardCharsets.UTF_8);

		assertTrue(state.contains("Map<AbilityCooldownKey, Cooldown> COOLDOWNS"),
			"Cooldowns should not be keyed only by player UUID; loadout swaps can select a different ability Focus.");
		assertTrue(state.contains("record AbilityCooldownKey(UUID playerId, String itemId)"),
			"Cooldown identity should follow the same ability Focus item across slot swaps.");
		assertTrue(!state.contains("record AbilityCooldownKey(UUID playerId, int slot, String itemId)"),
			"Slot-scoped cooldowns let players bypass cooldowns by moving the same ability Focus to another slot.");
		assertTrue(state.contains("AbilityCooldownKey cooldownKey(UUID playerId)"),
			"The selected ability should be able to derive its cooldown identity.");
		assertTrue(state.contains("cooldownRemaining(player, selection)"),
			"Trigger and sync paths should check the cooldown for the current selected ability only.");
		assertTrue(state.contains("COOLDOWNS.put(selection.cooldownKey(player.getUUID())"),
			"Starting a cooldown should store it under the current selected ability key.");
		assertTrue(state.contains("COOLDOWNS.remove(selection.cooldownKey(player.getUUID()))"),
			"Zero-cooldown successful abilities should clear only their own selected-ability key.");
	}
}
