package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link TemperingResolver} — the rules that decide when two
 * Foci may be tempered into one, and the attunement-cost surcharge a tempered
 * Focus carries.
 */
class TemperingResolverTest {

	@Test
	void temperingNeedsTwoCopiesOfTheSameFocus() {
		assertTrue(TemperingResolver.canTemper("attuned:rivet_focus", "attuned:rivet_focus", false, false),
			"Two untempered copies of the same Focus should be temperable.");
	}

	@Test
	void differentFociCannotBeTempered() {
		assertFalse(TemperingResolver.canTemper("attuned:rivet_focus", "attuned:edge_focus", false, false),
			"Tempering should require both inputs to be the same Focus.");
	}

	@Test
	void anEmptyIdCannotBeTempered() {
		assertFalse(TemperingResolver.canTemper("", "", false, false),
			"An empty Focus id is not a real Focus and cannot be tempered.");
	}

	@Test
	void anAlreadyTemperedFocusCannotBeTemperedAgain() {
		assertFalse(TemperingResolver.canTemper("attuned:rivet_focus", "attuned:rivet_focus", true, false),
			"A Focus that is already tempered cannot be tempered again.");
		assertFalse(TemperingResolver.canTemper("attuned:rivet_focus", "attuned:rivet_focus", false, true),
			"A Focus that is already tempered cannot be tempered again.");
		assertFalse(TemperingResolver.canTemper("attuned:rivet_focus", "attuned:rivet_focus", true, true),
			"Two tempered copies cannot be tempered further.");
	}

	@Test
	void temperedCostAddsOneAttunementPoint() {
		assertEquals(3, TemperingResolver.temperedCost(2),
			"A tempered Focus should cost one more attunement point than its base.");
		assertEquals(1, TemperingResolver.temperedCost(0),
			"Even a free Focus costs one attunement point once tempered.");
	}
}
