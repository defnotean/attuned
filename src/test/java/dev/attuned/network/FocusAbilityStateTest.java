package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FocusAbilityStateTest {
	@Test
	void cooldownMessageSecondsUsesCeilingWithoutOverflow() {
		assertEquals(1, FocusAbilityState.cooldownSecondsForMessage(1));
		assertEquals(1, FocusAbilityState.cooldownSecondsForMessage(20));
		assertEquals(2, FocusAbilityState.cooldownSecondsForMessage(21));
		assertEquals(107374183, FocusAbilityState.cooldownSecondsForMessage(Integer.MAX_VALUE));
	}
}
