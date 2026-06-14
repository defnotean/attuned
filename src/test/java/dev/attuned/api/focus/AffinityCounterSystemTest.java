package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Counter-system coverage for the promoted 8-value {@link Affinity} wheel. The
 * matrix mirrors {@link Aspect} exactly: every Affinity is strong against two
 * others and weak to two reciprocal others, and the original four-cycle
 * relationships survive as a subset.
 */
class AffinityCounterSystemTest {

	@Test
	void affinityWheelShipsTheEightPromotedCounterTypes() {
		assertEquals(List.of(
			Affinity.FURY,
			Affinity.BASTION,
			Affinity.ZEPHYR,
			Affinity.HOLY,
			Affinity.TIDE,
			Affinity.FORGE,
			Affinity.VERDANT,
			Affinity.UMBRAL), List.of(Affinity.values()));
		assertEquals("fury", Affinity.FURY.getSerializedName());
		assertEquals("tide", Affinity.TIDE.getSerializedName());
		assertEquals("forge", Affinity.FORGE.getSerializedName());
		assertEquals("verdant", Affinity.VERDANT.getSerializedName());
		assertEquals("umbral", Affinity.UMBRAL.getSerializedName());
	}

	@Test
	void affinityWheelMatchesThePlannedCounterMatrix() {
		assertCounters(Affinity.FURY, Affinity.BASTION, Affinity.VERDANT);
		assertCounters(Affinity.BASTION, Affinity.ZEPHYR, Affinity.UMBRAL);
		assertCounters(Affinity.ZEPHYR, Affinity.HOLY, Affinity.TIDE);
		assertCounters(Affinity.HOLY, Affinity.FURY, Affinity.UMBRAL);
		assertCounters(Affinity.TIDE, Affinity.FURY, Affinity.FORGE);
		assertCounters(Affinity.FORGE, Affinity.BASTION, Affinity.VERDANT);
		assertCounters(Affinity.VERDANT, Affinity.TIDE, Affinity.HOLY);
		assertCounters(Affinity.UMBRAL, Affinity.ZEPHYR, Affinity.FORGE);
	}

	@Test
	void everyAffinityHasExactlyTwoStrengthsAndTwoReciprocalWeaknesses() {
		for (Affinity affinity : Affinity.values()) {
			assertEquals(2, affinity.strongAgainst().size(), affinity + " should counter exactly two affinities");
			assertEquals(2, affinity.weakAgainst().size(), affinity + " should be countered by exactly two affinities");
			assertFalse(affinity.strongAgainst().contains(affinity), affinity + " should not counter itself");
			assertFalse(affinity.weakAgainst().contains(affinity), affinity + " should not be weak to itself");

			for (Affinity target : affinity.strongAgainst()) {
				assertTrue(target.weakAgainst().contains(affinity),
					affinity + " counters " + target + " but the weakness is not reciprocal");
			}
			for (Affinity counter : affinity.weakAgainst()) {
				assertTrue(counter.strongAgainst().contains(affinity),
					affinity + " is weak to " + counter + " but the strength is not reciprocal");
			}
		}
	}

	@Test
	void promotedMatrixPreservesTheOriginalFourCycleAsASubset() {
		// The historic four-cycle (Holy > Fury > Bastion > Zephyr > Holy) must
		// still hold inside the expanded wheel so legacy builds read the same.
		assertTrue(Affinity.FURY.beats(Affinity.BASTION), "Fury should still beat Bastion");
		assertTrue(Affinity.BASTION.beats(Affinity.ZEPHYR), "Bastion should still beat Zephyr");
		assertTrue(Affinity.ZEPHYR.beats(Affinity.HOLY), "Zephyr should still beat Holy");
		assertTrue(Affinity.HOLY.beats(Affinity.FURY), "Holy should still beat Fury");
	}

	@Test
	void everyAffinityCarriesANonNeutralColor() {
		for (Affinity affinity : Affinity.values()) {
			int argb = affinity.argb();
			assertEquals(0xFF, (argb >>> 24) & 0xFF, affinity + " color should be fully opaque");
			assertTrue((argb & 0x00FFFFFF) != (AffinityColors.NEUTRAL_ARGB & 0x00FFFFFF),
				affinity + " should not paint as the neutral grey");
		}
	}

	private static void assertCounters(Affinity affinity, Affinity first, Affinity second) {
		assertEquals(EnumSet.of(first, second), affinity.strongAgainst());
		assertTrue(affinity.beats(first), affinity + " should beat " + first);
		assertTrue(affinity.beats(second), affinity + " should beat " + second);
	}
}
