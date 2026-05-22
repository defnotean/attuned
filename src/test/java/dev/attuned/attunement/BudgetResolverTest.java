package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.attunement.BudgetResolver.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link BudgetResolver} — the budget, slot-priority,
 * dormancy, affinity-commit and uniqueness rules of the attunement economy.
 */
class BudgetResolverTest {

	/** A non-unique Focus at {@code slot} with a given cost and affinity (null = neutral). */
	private static Candidate<String, String> focus(int slot, int cost, String affinity) {
		return new Candidate<>(slot, cost, affinity, false, "focus" + slot);
	}

	/** A unique Focus at {@code slot} whose duplicate-detection identity is {@code id}. */
	private static Candidate<String, String> unique(int slot, int cost, String id) {
		return new Candidate<>(slot, cost, null, true, id);
	}

	@Test
	void everythingFitsWithinBudget() {
		assertEquals(List.of(0, 1, 2), BudgetResolver.resolve(
			List.of(focus(0, 2, null), focus(1, 3, null), focus(2, 1, null)), 10));
	}

	@Test
	void zeroBudgetActivatesNothing() {
		assertEquals(List.of(), BudgetResolver.resolve(
			List.of(focus(0, 2, null), focus(1, 1, null)), 0));
	}

	@Test
	void overBudgetFocusGoesDormant() {
		// Budget 5: costs 3 + 3 — the second does not fit.
		assertEquals(List.of(0), BudgetResolver.resolve(
			List.of(focus(0, 3, null), focus(1, 3, null)), 5));
	}

	@Test
	void aLaterCheaperFocusStillFitsAfterADormantOne() {
		// Budget 5: 3 fits (used 3), 3 does not, 2 fits (used 5).
		assertEquals(List.of(0, 2), BudgetResolver.resolve(
			List.of(focus(0, 3, null), focus(1, 3, null), focus(2, 2, null)), 5));
	}

	@Test
	void slotOrderIsPriority() {
		// Budget 3: only the first slot's Focus fits.
		assertEquals(List.of(0), BudgetResolver.resolve(
			List.of(focus(0, 3, null), focus(1, 3, null)), 3));
	}

	@Test
	void firstAffinityCommitsTheLane() {
		// Fury commits at slot 0; the off-lane Bastion Focus is dormant.
		assertEquals(List.of(0, 2), BudgetResolver.resolve(
			List.of(focus(0, 2, "fury"), focus(1, 2, "bastion"), focus(2, 2, "fury")), 20));
	}

	@Test
	void neutralFociActivateAlongsideAnyLane() {
		assertEquals(List.of(0, 1, 2), BudgetResolver.resolve(
			List.of(focus(0, 2, "zephyr"), focus(1, 2, null), focus(2, 2, "zephyr")), 20));
	}

	@Test
	void aDormantOffLaneFocusConsumesNoBudget() {
		// Fury commits at slot 0; slot 1 (bastion, cost 99) is off-lane and skipped,
		// so slot 2 (fury, cost 5) must still fit within budget 7.
		assertEquals(List.of(0, 2), BudgetResolver.resolve(
			List.of(focus(0, 2, "fury"), focus(1, 99, "bastion"), focus(2, 5, "fury")), 7));
	}

	@Test
	void anOverBudgetAffinityFocusDoesNotCommitTheLane() {
		// Slot 0 is fury but over budget — dormant, commits nothing — so the
		// bastion Focus at slot 1 is free to commit the lane instead.
		assertEquals(List.of(1), BudgetResolver.resolve(
			List.of(focus(0, 99, "fury"), focus(1, 2, "bastion")), 10));
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
