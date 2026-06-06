package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves the active Focus layout and attunement budget into an Apex capstone. */
final class ApexCapstoneResolver {
	private static final int MIN_FOCI = 4;
	private static final int BUDGET_SLACK = 1;

	private ApexCapstoneResolver() {}

	static Optional<Apex.Capstone> resolve(List<Optional<Affinity>> activeAffinities,
			int used, int capacity) {
		Objects.requireNonNull(activeAffinities, "activeAffinities");
		if (!hasEnoughActiveFoci(activeAffinities) || !isNearFullBudget(used, capacity)) {
			return Optional.empty();
		}

		Layout layout = Layout.from(activeAffinities);
		if (layout.isAllNeutral(activeAffinities.size())) {
			return Optional.of(Apex.Capstone.STILLPOINT);
		}
		if (layout.isPureSingleAffinity()) {
			return Optional.of(Apex.Capstone.ofAffinity(layout.onlyAffinity()));
		}
		if (layout.hasEveryAffinity()) {
			return Optional.of(Apex.Capstone.MAELSTROM);
		}
		return Optional.empty();
	}

	private static boolean hasEnoughActiveFoci(List<Optional<Affinity>> activeAffinities) {
		return activeAffinities.size() >= MIN_FOCI;
	}

	private static boolean isNearFullBudget(int used, int capacity) {
		return used <= capacity && capacity - used <= BUDGET_SLACK;
	}

	private record Layout(EnumMap<Affinity, Integer> counts, int neutral) {
		private static Layout from(List<Optional<Affinity>> activeAffinities) {
			EnumMap<Affinity, Integer> counts = new EnumMap<>(Affinity.class);
			int neutral = 0;
			for (Optional<Affinity> affinity : activeAffinities) {
				Objects.requireNonNull(affinity, "affinity");
				if (affinity.isPresent()) {
					counts.merge(affinity.get(), 1, Integer::sum);
				} else {
					neutral++;
				}
			}
			return new Layout(counts, neutral);
		}

		private boolean isAllNeutral(int activeFocusCount) {
			return counts.isEmpty() && neutral == activeFocusCount;
		}

		private boolean isPureSingleAffinity() {
			return counts.size() == 1 && neutral == 0;
		}

		private boolean hasEveryAffinity() {
			return counts.size() == Affinity.values().length;
		}

		private Affinity onlyAffinity() {
			return counts.keySet().iterator().next();
		}
	}
}
