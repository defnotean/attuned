package dev.attuned.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class ReweavingResultPicker {
	public static final int WEIGHT_MATCHING_COMMITTED = 4;
	public static final int WEIGHT_NEUTRAL = 2;
	public static final int WEIGHT_OTHER_AFFINITY = 1;

	private ReweavingResultPicker() {
	}

	public record Candidate(String id, Optional<String> affinity) {
		public Candidate {
			id = Objects.requireNonNull(id, "id");
			affinity = Objects.requireNonNull(affinity, "affinity");
		}
	}

	public record WeightedCandidate(String id, int weight) {
		public WeightedCandidate {
			id = Objects.requireNonNull(id, "id");
			if (weight <= 0) {
				throw new IllegalArgumentException("weight must be positive");
			}
		}
	}

	public static Optional<String> pick(
		List<Candidate> pool,
		Set<String> sacrificedIds,
		Optional<String> committedAffinity,
		RandomGenerator random
	) {
		Objects.requireNonNull(random, "random");
		List<WeightedCandidate> candidates = weightedCandidates(pool, sacrificedIds, committedAffinity);
		if (candidates.isEmpty() && !sacrificedIds.isEmpty()) {
			candidates = weightedCandidates(pool, Set.of(), committedAffinity);
		}

		int totalWeight = candidates.stream()
			.mapToInt(WeightedCandidate::weight)
			.sum();
		if (totalWeight <= 0) {
			return Optional.empty();
		}

		int roll = random.nextInt(totalWeight);
		for (WeightedCandidate candidate : candidates) {
			roll -= candidate.weight();
			if (roll < 0) {
				return Optional.of(candidate.id());
			}
		}
		return Optional.empty();
	}

	static List<WeightedCandidate> weightedCandidates(
		List<Candidate> pool,
		Set<String> sacrificedIds,
		Optional<String> committedAffinity
	) {
		Objects.requireNonNull(pool, "pool");
		Objects.requireNonNull(sacrificedIds, "sacrificedIds");
		Objects.requireNonNull(committedAffinity, "committedAffinity");
		return pool.stream()
			.map(candidate -> Objects.requireNonNull(candidate, "candidate"))
			.filter(candidate -> !sacrificedIds.contains(candidate.id()))
			.map(candidate -> new WeightedCandidate(candidate.id(), weight(candidate, committedAffinity)))
			.toList();
	}

	private static int weight(Candidate candidate, Optional<String> committedAffinity) {
		if (candidate.affinity().isEmpty()) {
			return WEIGHT_NEUTRAL;
		}
		if (committedAffinity.isPresent() && committedAffinity.equals(candidate.affinity())) {
			return WEIGHT_MATCHING_COMMITTED;
		}
		return WEIGHT_OTHER_AFFINITY;
	}
}
