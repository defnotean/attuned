package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AspectCounterSystemTest {

	@Test
	void aspectWheelShipsTheEightPlannedCounterTypes() {
		assertEquals(List.of(
			Aspect.FURY,
			Aspect.BASTION,
			Aspect.ZEPHYR,
			Aspect.HOLY,
			Aspect.TIDE,
			Aspect.FORGE,
			Aspect.VERDANT,
			Aspect.UMBRAL), List.of(Aspect.values()));
		assertEquals("attuned:fury", Aspect.FURY.id().toString());
		assertEquals("attuned:tide", Aspect.TIDE.id().toString());
		assertEquals("tide", Aspect.TIDE.getSerializedName());
	}

	@Test
	void aspectWheelMatchesThePlannedCounterMatrix() {
		assertCounters(Aspect.FURY, Aspect.BASTION, Aspect.VERDANT);
		assertCounters(Aspect.BASTION, Aspect.ZEPHYR, Aspect.UMBRAL);
		assertCounters(Aspect.ZEPHYR, Aspect.HOLY, Aspect.TIDE);
		assertCounters(Aspect.HOLY, Aspect.FURY, Aspect.UMBRAL);
		assertCounters(Aspect.TIDE, Aspect.FURY, Aspect.FORGE);
		assertCounters(Aspect.FORGE, Aspect.BASTION, Aspect.VERDANT);
		assertCounters(Aspect.VERDANT, Aspect.TIDE, Aspect.HOLY);
		assertCounters(Aspect.UMBRAL, Aspect.ZEPHYR, Aspect.FORGE);
	}

	@Test
	void everyAspectHasExactlyTwoStrengthsAndTwoReciprocalWeaknesses() {
		for (Aspect aspect : Aspect.values()) {
			assertEquals(2, aspect.strongAgainst().size(), aspect + " should counter exactly two aspects");
			assertEquals(2, aspect.weakAgainst().size(), aspect + " should be countered by exactly two aspects");
			assertFalse(aspect.strongAgainst().contains(aspect), aspect + " should not counter itself");
			assertFalse(aspect.weakAgainst().contains(aspect), aspect + " should not be weak to itself");

			for (Aspect target : aspect.strongAgainst()) {
				assertTrue(target.weakAgainst().contains(aspect),
					aspect + " counters " + target + " but the weakness is not reciprocal");
			}
			for (Aspect counter : aspect.weakAgainst()) {
				assertTrue(counter.strongAgainst().contains(aspect),
					aspect + " is weak to " + counter + " but the strength is not reciprocal");
			}
		}
	}

	private static void assertCounters(Aspect aspect, Aspect first, Aspect second) {
		assertEquals(EnumSet.of(first, second), aspect.strongAgainst());
		assertTrue(aspect.beats(first), aspect + " should beat " + first);
		assertTrue(aspect.beats(second), aspect + " should beat " + second);
	}
}
