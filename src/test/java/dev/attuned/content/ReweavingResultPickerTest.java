package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class ReweavingResultPickerTest {
	@Test
	void pickSameAffinityExcludesInputId() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:ember_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:forager_focus", Optional.empty()));

		Optional<String> result = ReweavingResultPicker.pickSameAffinity(
			pool, "attuned:edge_focus", Optional.of("fury"), new FixedRandom(0));

		assertEquals(Optional.of("attuned:ember_focus"), result);
	}

	@Test
	void pickSameAffinityNeutralInputOnlyPicksNeutralCandidates() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:forager_focus", Optional.empty()),
			new ReweavingResultPicker.Candidate("attuned:linecast_focus", Optional.empty()),
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")));

		Optional<String> first = ReweavingResultPicker.pickSameAffinity(
			pool, "attuned:forager_focus", Optional.empty(), new FixedRandom(0));
		Optional<String> second = ReweavingResultPicker.pickSameAffinity(
			pool, "attuned:forager_focus", Optional.empty(), new FixedRandom(1));

		assertEquals(Optional.of("attuned:linecast_focus"), first);
		assertEquals(Optional.of("attuned:linecast_focus"), second);
	}

	@Test
	void pickSameAffinityFuryInputOnlyPicksFuryCandidates() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:ember_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:forager_focus", Optional.empty()),
			new ReweavingResultPicker.Candidate("attuned:swift_focus", Optional.of("zephyr")));

		Optional<String> first = ReweavingResultPicker.pickSameAffinity(
			pool, "attuned:edge_focus", Optional.of("fury"), new FixedRandom(0));
		Optional<String> second = ReweavingResultPicker.pickSameAffinity(
			pool, "attuned:edge_focus", Optional.of("fury"), new FixedRandom(1));

		assertEquals(Optional.of("attuned:ember_focus"), first);
		assertEquals(Optional.of("attuned:ember_focus"), second);
	}

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

	@Test
	void pickMapsRollsAcrossWeightedCandidateRanges() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")),
			new ReweavingResultPicker.Candidate("attuned:forager_focus", Optional.empty()),
			new ReweavingResultPicker.Candidate("attuned:swift_focus", Optional.of("zephyr")));

		assertEquals(Optional.of("attuned:edge_focus"), pickWithRoll(pool, 0));
		assertEquals(Optional.of("attuned:edge_focus"), pickWithRoll(pool, 3));
		assertEquals(Optional.of("attuned:forager_focus"), pickWithRoll(pool, 4));
		assertEquals(Optional.of("attuned:forager_focus"), pickWithRoll(pool, 5));
		assertEquals(Optional.of("attuned:swift_focus"), pickWithRoll(pool, 6));
	}

	@Test
	void candidateRecordsRejectInvalidInputs() {
		assertThrows(NullPointerException.class,
			() -> new ReweavingResultPicker.Candidate(null, Optional.empty()));
		assertThrows(NullPointerException.class,
			() -> new ReweavingResultPicker.Candidate("attuned:edge_focus", null));
		assertThrows(NullPointerException.class,
			() -> new ReweavingResultPicker.WeightedCandidate(null, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new ReweavingResultPicker.WeightedCandidate("attuned:edge_focus", 0));
	}

	@Test
	void pickerMethodsRejectNullInputsClearly() {
		List<ReweavingResultPicker.Candidate> pool = List.of(
			new ReweavingResultPicker.Candidate("attuned:edge_focus", Optional.of("fury")));

		NullPointerException missingPool = assertThrows(NullPointerException.class,
			() -> ReweavingResultPicker.pick(null, Set.of(), Optional.empty(), new FixedRandom(0)));
		assertEquals("pool", missingPool.getMessage());

		NullPointerException missingSacrificedIds = assertThrows(NullPointerException.class,
			() -> ReweavingResultPicker.pick(pool, null, Optional.empty(), new FixedRandom(0)));
		assertEquals("sacrificedIds", missingSacrificedIds.getMessage());

		NullPointerException missingCommittedAffinity = assertThrows(NullPointerException.class,
			() -> ReweavingResultPicker.pick(pool, Set.of(), null, new FixedRandom(0)));
		assertEquals("committedAffinity", missingCommittedAffinity.getMessage());

		NullPointerException missingRandom = assertThrows(NullPointerException.class,
			() -> ReweavingResultPicker.pick(List.of(), Set.of(), Optional.empty(), null));
		assertEquals("random", missingRandom.getMessage());

		List<ReweavingResultPicker.Candidate> candidates = new ArrayList<>();
		candidates.add(null);

		NullPointerException missingCandidate = assertThrows(NullPointerException.class,
			() -> ReweavingResultPicker.weightedCandidates(candidates, Set.of(), Optional.empty()));
		assertEquals("candidate", missingCandidate.getMessage());
	}

	private static Optional<String> pickWithRoll(List<ReweavingResultPicker.Candidate> pool, int roll) {
		return ReweavingResultPicker.pick(pool, Set.of(), Optional.of("fury"), new FixedRandom(roll));
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
