package dev.attuned.attunement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The pure attunement-budget resolution: given a player's occupied Focus slots
 * in priority order, which ones end up active.
 *
 * <p>This is the policy at the heart of Attuned, kept free of any Minecraft type
 * so it can be reasoned about and unit-tested in isolation. {@link Attunement}
 * gathers the candidates from a player and delegates the decision here.
 *
 * <p>Walking the candidates in priority order, a Focus activates when:
 * <ul>
 *   <li>its cost still fits the remaining budget;</li>
 *   <li>its affinity matches the committed one — the affinity of the first
 *       affinity-bearing Focus to activate (a {@code null} affinity is neutral
 *       and always eligible);</li>
 *   <li>and, if it is unique, no copy with the same identity is active yet.</li>
 * </ul>
 * A candidate that fails any check is dormant and consumes no budget.
 *
 * @param <A> the affinity token type — compared only for equality
 * @param <I> the identity token type used to detect duplicate unique Foci
 */
public final class BudgetResolver {
	private BudgetResolver() {}

	/**
	 * One occupied Focus slot's resolution-relevant facts.
	 *
	 * @param slot     the slot index this candidate occupies
	 * @param cost     attunement points the Focus consumes when active
	 * @param affinity the Focus affinity, or {@code null} if affinity-neutral
	 * @param unique   whether at most one copy of this Focus may be active
	 * @param identity the token identifying "the same Focus" for the unique rule
	 */
	public record Candidate<A, I>(int slot, int cost, A affinity, boolean unique, I identity) {}

	/**
	 * Resolves which candidates are active. {@code candidates} must be in
	 * priority order; the returned slot indices preserve that order.
	 */
	public static <A, I> List<Integer> resolve(List<Candidate<A, I>> candidates, int budget) {
		int used = 0;
		A committed = null;
		Set<I> activeUnique = new HashSet<>();
		List<Integer> active = new ArrayList<>();
		for (Candidate<A, I> candidate : candidates) {
			// A later copy of a unique Focus stays dormant.
			if (candidate.unique() && activeUnique.contains(candidate.identity())) {
				continue;
			}
			// Once an affinity is committed, a mismatched one stays dormant.
			if (candidate.affinity() != null && committed != null
					&& !committed.equals(candidate.affinity())) {
				continue;
			}
			// An over-budget Focus is dormant and commits nothing.
			if (used + candidate.cost() > budget) {
				continue;
			}
			used += candidate.cost();
			active.add(candidate.slot());
			if (candidate.unique()) {
				activeUnique.add(candidate.identity());
			}
			if (candidate.affinity() != null && committed == null) {
				committed = candidate.affinity();
			}
		}
		return active;
	}
}
