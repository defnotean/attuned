package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class ReweavingResultPickerTest {
	@Test
	void avoidsSacrificedIdsWhenAlternativesExist() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:iron_focus", Optional.of("bastion")),
			new ReweavingResultPicker.Candidate("attuned:swift_focus", Optional.of("zephyr")),
			new ReweavingResultPicker.Candidate("attuned:forager_focus", Optional.empty()),
			new ReweavingResultPicker.Candidate("attuned:linecast_focus", Optional.empty()));
		Set<String> sacrificed = Set.of(
			"attuned:edge_focus",
			"attuned:iron_focus",
			"attuned:swift_focus");

		Optional<String> result = ReweavingResultPicker.pick(
			pool, sacrificed, Optional.empty(), new FixedRandom(0));

		assertTrue(result.isPresent(), "Expected a reweaving result");
		assertTrue(!sacrificed.contains(result.orElseThrow()),
			"Reweaving should avoid sacrificed Foci when alternatives exist");
	}

	@Test
	void allowsSacrificedIdsOnlyWhenPoolHasNoAlternative() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")));

		Optional<String> result = ReweavingResultPicker.pick(
			pool, Set.of("attuned:edge_focus"), Optional.empty(), new FixedRandom(0));

		assertEquals(Optional.of("attuned:edge_focus"), result);
	}

	@Test
	void committedAffinityReceivesWeightBonusButDoesNotExcludeOthers() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:forager_focus", Optional.empty()),
			new ReweavingResultPicker.Candidate("attuned:swift_focus", Optional.of("zephyr")));

		List<ReweavingResultPicker.WeightedCandidate> weighted =
			ReweavingResultPicker.weightedCandidates(pool, Set.of(), Optional.of("fury"));

		int fury = weightOf(weighted, "attuned:edge_focus");
		int neutral = weightOf(weighted, "attuned:forager_focus");
		int zephyr = weightOf(weighted, "attuned:swift_focus");
		assertTrue(fury > neutral, "Matching committed affinity should have the highest weight");
		assertTrue(neutral > zephyr, "Neutral candidates should still outrank other affinities");
	}

	private static int weightOf(List<ReweavingResultPicker.WeightedCandidate> weighted, String id) {
		return weighted.stream()
			.filter(candidate -> candidate.id().equals(id))
			.findFirst()
			.orElseThrow()
			.weight();
	}

	private record FixedRandom(int value) implements RandomGenerator {
		@Override
		public int nextInt(int bound) {
			return Math.floorMod(value, bound);
		}

		@Override
		public long nextLong() {
			return value;
		}
	}
}
