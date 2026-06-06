package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.attuned.attunement.BudgetResolver.Candidate;
import dev.attuned.attunement.BudgetResolver.DormantReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
	void negativeBudgetIsRejected() {
		assertThrows(IllegalArgumentException.class,
			() -> BudgetResolver.resolve(List.of(focus(0, 0)), -1),
			"Attunement budget should not represent negative capacity.");
		assertThrows(IllegalArgumentException.class,
			() -> BudgetResolver.resolveDetailed(List.of(focus(0, 0)), -1),
			"Detailed attunement budget should not represent negative capacity.");
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
	void detailedResolutionReportsOverBudgetReasonAndStillActivatesLaterCheaperFocus() {
		BudgetResolver.Resolution resolution = BudgetResolver.resolveDetailed(
			List.of(focus(0, 3), focus(1, 3), focus(2, 2)), 5);

		assertEquals(List.of(0, 2), resolution.activeSlots());
		assertEquals(Map.of(1, DormantReason.NOT_ENOUGH_CAPACITY), resolution.dormantReasons());
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
	void detailedResolutionReportsDuplicateUniqueReason() {
		BudgetResolver.Resolution resolution = BudgetResolver.resolveDetailed(
			List.of(unique(0, 2, "shard"), unique(1, 2, "shard")), 20);

		assertEquals(List.of(0), resolution.activeSlots());
		assertEquals(Map.of(1, DormantReason.DUPLICATE_UNIQUE), resolution.dormantReasons());
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
	void detailedResolutionDoesNotClaimUniqueIdentityWhenDormantFromBudget() {
		BudgetResolver.Resolution resolution = BudgetResolver.resolveDetailed(
			List.of(unique(0, 99, "shard"), unique(1, 2, "shard")), 10);

		assertEquals(List.of(1), resolution.activeSlots());
		assertEquals(Map.of(0, DormantReason.NOT_ENOUGH_CAPACITY), resolution.dormantReasons());
	}

	@Test
	void resolveDelegatesToDetailedResolutionActiveSlots() {
		List<Candidate<String>> candidates =
			List.of(focus(0, 3), focus(1, 3), focus(2, 2));

		assertEquals(BudgetResolver.resolveDetailed(candidates, 5).activeSlots(),
			BudgetResolver.resolve(candidates, 5));
	}

	@Test
	void noCandidatesResolvesEmpty() {
		assertEquals(List.of(), BudgetResolver.resolve(List.of(), 20));
	}

	@Test
	void candidateRejectsInvalidSlotAndCost() {
		assertThrows(IllegalArgumentException.class, () -> new Candidate<>(-1, 1, false, "focus"),
			"Budget candidates should not represent negative inventory slots.");
		assertThrows(IllegalArgumentException.class, () -> new Candidate<>(0, -1, false, "focus"),
			"Budget candidates should not represent negative attunement costs.");
	}

	@Test
	void uniqueCandidateRejectsNullIdentity() {
		assertThrows(NullPointerException.class, () -> new Candidate<String>(0, 1, true, null),
			"Unique budget candidates need a real identity token for duplicate detection.");
	}

	@Test
	void resolverRejectsNullCandidateInputsClearly() {
		NullPointerException missingList = assertThrows(NullPointerException.class,
			() -> BudgetResolver.resolveDetailed(null, 0));
		assertEquals("candidates", missingList.getMessage());

		List<Candidate<String>> candidates = new ArrayList<>();
		candidates.add(null);

		NullPointerException missingCandidate = assertThrows(NullPointerException.class,
			() -> BudgetResolver.resolveDetailed(candidates, 0));
		assertEquals("candidate", missingCandidate.getMessage());
	}
}
