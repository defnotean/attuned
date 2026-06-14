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
	/**
	 * Maelstrom's diversity gate after the 8-affinity promotion: a build needs at
	 * least this many distinct active affinities, with no single affinity stacked
	 * to {@link #UNTETHERED_MAX_PER_AFFINITY} or more (which would read as a
	 * committed lane rather than a true Manifold spread).
	 */
	private static final int UNTETHERED_MIN_DISTINCT = 4;
	private static final int UNTETHERED_MAX_PER_AFFINITY = 3;

	private ApexCapstoneResolver() {}

	static Optional<Apex.Capstone> resolve(List<Optional<Affinity>> activeAffinities,
			int used, int capacity) {
		Objects.requireNonNull(activeAffinities, "activeAffinities");
		requireValidAffinities(activeAffinities);
		requireNonNegativeBudget(used, capacity);
		if (!hasEnoughActiveFoci(activeAffinities) || !isNearFullBudget(used, capacity)) {
			return Optional.empty();
		}

		Layout layout = Layout.from(activeAffinities);
		if (layout.isAllNeutral(activeAffinities.size())) {
			return Optional.of(Apex.Capstone.STILLPOINT);
		}
		if (layout.isPureSingleAffinity()) {
			// A promoted affinity (Tide/Forge/Verdant/Umbral) owns no capstone, so
			// a build committed purely to one of them resolves to no Apex.
			return Apex.Capstone.ofAffinity(layout.onlyAffinity());
		}
		if (layout.isManifoldSpread()) {
			return Optional.of(Apex.Capstone.MAELSTROM);
		}
		return Optional.empty();
	}

	private static void requireValidAffinities(List<Optional<Affinity>> activeAffinities) {
		for (Optional<Affinity> affinity : activeAffinities) {
			Objects.requireNonNull(affinity, "affinity");
		}
	}

	private static void requireNonNegativeBudget(int used, int capacity) {
		if (used < 0) {
			throw new IllegalArgumentException("used budget must be non-negative");
		}
		if (capacity < 0) {
			throw new IllegalArgumentException("capacity must be non-negative");
		}
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

		/**
		 * Maelstrom's Manifold gate after the 8-affinity promotion: four or more
		 * distinct active affinities with no single affinity stacked three or more
		 * deep. Requiring all eight would be unrealistic, so diversity (not a full
		 * sweep) keys the high-Discord capstone.
		 */
		private boolean isManifoldSpread() {
			if (counts.size() < UNTETHERED_MIN_DISTINCT) {
				return false;
			}
			for (int count : counts.values()) {
				if (count >= UNTETHERED_MAX_PER_AFFINITY) {
					return false;
				}
			}
			return true;
		}

		private Affinity onlyAffinity() {
			return counts.keySet().iterator().next();
		}
	}
}
