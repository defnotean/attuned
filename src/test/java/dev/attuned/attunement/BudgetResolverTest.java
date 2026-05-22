package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.attunement.BudgetResolver.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link BudgetResolver} — the budget, slot-priority,
 * dormancy and uniqueness rules of the attunement economy.
 */
class BudgetResolverTest {

	/** A non-unique Focus at {@code slot} with a given cost. */
	private static Candidate<String> focus(int slot, int cost) {
		return new Candidate<>(slot, cost, false, "focus" + slot);
	}

	/** A unique Focus at {@code slot} whose duplicate-detection identity is {@code id}. */
	private static Candidate<String> unique(int slot, int cost, String id) {
		return new Candidate<>(slot, cost, true, id);
	}

	@Test
	void everythingFitsWithinBudget() {
		assertEquals(List.of(0, 1, 2), BudgetResolver.resolve(
			List.of(focus(0, 2), focus(1, 3), focus(2, 1)), 10));
	}

	@Test
	void zeroBudgetActivatesNothing() {
		assertEquals(List.of(), BudgetResolver.resolve(
			List.of(focus(0, 2), focus(1, 1)), 0));
	}

	@Test
	void overBudgetFocusGoesDormant() {
		// Budget 5: costs 3 + 3 — the second does not fit.
		assertEquals(List.of(0), BudgetResolver.resolve(
			List.of(focus(0, 3), focus(1, 3)), 5));
	}

	@Test
	void aLaterCheaperFocusStillFitsAfterADormantOne() {
		// Budget 5: 3 fits (used 3), 3 does not, 2 fits (used 5).
		assertEquals(List.of(0, 2), BudgetResolver.resolve(
			List.of(focus(0, 3), focus(1, 3), focus(2, 2)), 5));
	}

	@Test
	void slotOrderIsPriority() {
		// Budget 3: only the first slot's Focus fits.
		assertEquals(List.of(0), BudgetResolver.resolve(
			List.of(focus(0, 3), focus(1, 3)), 3));
	}

	@Test
	void aDuplicateUniqueFocusStaysDormant() {
		assertEquals(List.of(0), BudgetResolver.resolve(
			List.of(unique(0, 2, "shard"), unique(1, 2, "shard")), 20));
	}

	@Test
	void distinctUniqueFociBothActivate() {
		assertEquals(List.of(0, 1), BudgetResolver.resolve(
			List.of(unique(0, 2, "alpha"), unique(1, 2, "beta")), 20));
	}

	@Test
	void aUniqueFocusDormantOnBudgetDoesNotBlockALaterCopy() {
		// Slot 0's unique Focus is over budget — it never claims the identity,
		// so slot 1's copy is free to activate.
		assertEquals(List.of(1), BudgetResolver.resolve(
			List.of(unique(0, 99, "shard"), unique(1, 2, "shard")), 10));
	}

	@Test
	void noCandidatesResolvesEmpty() {
		assertEquals(List.of(), BudgetResolver.resolve(List.of(), 20));
	}
}
