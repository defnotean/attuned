package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionBarMessagesTest {

	@Test
	void higherPriorityCanInterruptLowerPriorityDuringHoldWindow() {
		ActionBarMessages.Decision previous = new ActionBarMessages.Decision(
			ActionBarMessages.Priority.AMBIENT, 100L);

		assertTrue(ActionBarMessages.shouldDisplay(previous,
			ActionBarMessages.Priority.CRITICAL, 105L, 20L));
	}

	@Test
	void lowerOrEqualPriorityWaitsUntilHoldWindowExpires() {
		ActionBarMessages.Decision previous = new ActionBarMessages.Decision(
			ActionBarMessages.Priority.ABILITY, 100L);

		assertFalse(ActionBarMessages.shouldDisplay(previous,
			ActionBarMessages.Priority.PROGRESS, 110L, 20L));
		assertFalse(ActionBarMessages.shouldDisplay(previous,
			ActionBarMessages.Priority.ABILITY, 110L, 20L));
		assertTrue(ActionBarMessages.shouldDisplay(previous,
			ActionBarMessages.Priority.PROGRESS, 120L, 20L));
	}

	@Test
	void missingOrExpiredPreviousMessageAllowsDisplay() {
		assertTrue(ActionBarMessages.shouldDisplay(null,
			ActionBarMessages.Priority.AMBIENT, 5L, 20L));

		ActionBarMessages.Decision previous = new ActionBarMessages.Decision(
			ActionBarMessages.Priority.CRITICAL, 10L);
		assertTrue(ActionBarMessages.shouldDisplay(previous,
			ActionBarMessages.Priority.AMBIENT, 40L, 20L));
	}
}
