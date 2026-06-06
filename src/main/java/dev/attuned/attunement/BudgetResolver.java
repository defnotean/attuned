package dev.attuned.attunement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The pure attunement-budget resolution: given a player's occupied Focus slots
 * in priority order, which ones end up active.
 *
 * <p>This is the policy at the heart of Attuned, kept free of any Minecraft type
 * so it can be reasoned about and unit-tested in isolation. {@link Attunement}
 * gathers the candidates from a player and delegates the decision here.
 *
 * <p>Walking the candidates in priority order, a Focus activates when its cost
 * still fits the remaining budget and — if it is unique — no copy with the same
 * identity is active yet. A candidate that fails either check is dormant and
 * consumes no budget. Affinity does not gate activation: a build spanning
 * several affinities simply resolves to Discord, which {@link Attunement}
 * derives from the active set.
 *
 * @param <I> the identity token type used to detect duplicate unique Foci
 */
public final class BudgetResolver {
	private BudgetResolver() {}

	/**
	 * One occupied Focus slot's resolution-relevant facts.
	 *
	 * @param slot     the slot index this candidate occupies
	 * @param cost     attunement points the Focus consumes when active
	 * @param unique   whether at most one copy of this Focus may be active
	 * @param identity the token identifying "the same Focus" for the unique rule
	 */
	public record Candidate<I>(int slot, int cost, boolean unique, I identity) {
		public Candidate {
			if (slot < 0) {
				throw new IllegalArgumentException("slot must be non-negative");
			}
			if (cost < 0) {
				throw new IllegalArgumentException("cost must be non-negative");
			}
			if (unique) {
				Objects.requireNonNull(identity, "identity");
			}
		}
	}

	/** Why an occupied Focus slot was skipped during budget resolution. */
	public enum DormantReason {
		NOT_ENOUGH_CAPACITY,
		DUPLICATE_UNIQUE
	}

	/**
	 * The full budget-resolution result: active slots plus per-slot dormancy
	 * reasons in candidate priority order.
	 */
	public record Resolution(List<Integer> activeSlots, Map<Integer, DormantReason> dormantReasons) {
		public Resolution {
			activeSlots = List.copyOf(activeSlots);
			dormantReasons = Collections.unmodifiableMap(new LinkedHashMap<>(dormantReasons));
		}
	}

	/**
	 * Resolves which candidates are active. {@code candidates} must be in
	 * priority order; the returned slot indices preserve that order.
	 */
	public static <I> List<Integer> resolve(List<Candidate<I>> candidates, int budget) {
		return resolveDetailed(candidates, budget).activeSlots();
	}

	/**
	 * Resolves active slots and records why each skipped candidate is dormant.
	 * {@code candidates} must be in priority order.
	 */
	public static <I> Resolution resolveDetailed(List<Candidate<I>> candidates, int budget) {
		Objects.requireNonNull(candidates, "candidates");
		requireNonNegativeBudget(budget);
		int used = 0;
		Set<I> activeUnique = new HashSet<>();
		List<Integer> active = new ArrayList<>();
		Map<Integer, DormantReason> dormantReasons = new LinkedHashMap<>();
		for (Candidate<I> candidate : candidates) {
			Objects.requireNonNull(candidate, "candidate");
			// A later copy of a unique Focus stays dormant.
			if (candidate.unique() && activeUnique.contains(candidate.identity())) {
				dormantReasons.put(candidate.slot(), DormantReason.DUPLICATE_UNIQUE);
				continue;
			}
			// An over-budget Focus is dormant and consumes nothing.
			if (candidate.cost() > budget - used) {
				dormantReasons.put(candidate.slot(), DormantReason.NOT_ENOUGH_CAPACITY);
				continue;
			}
			used += candidate.cost();
			active.add(candidate.slot());
			if (candidate.unique()) {
				activeUnique.add(candidate.identity());
			}
		}
		return new Resolution(active, dormantReasons);
	}

	private static void requireNonNegativeBudget(int budget) {
		if (budget < 0) {
			throw new IllegalArgumentException("budget must be non-negative");
		}
	}
}
