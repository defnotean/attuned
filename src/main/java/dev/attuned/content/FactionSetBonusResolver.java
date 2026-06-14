package dev.attuned.content;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure resolver for faction set bonuses: when {@code threshold} or more ACTIVE
 * Foci share a faction, that faction grants a small passive perk.
 *
 * <p>Minecraft-free so it can be unit-tested in isolation. The runtime applier
 * {@link dev.attuned.content.behavior.FactionSetBonuses} derives the faction id
 * per active Focus and feeds it here; the empty string stands for a Focus with
 * no faction and never counts toward any bonus.
 */
public final class FactionSetBonusResolver {
	/** Default number of like-faction active Foci needed to wake a set bonus. */
	public static final int DEFAULT_THRESHOLD = 3;

	private FactionSetBonusResolver() {}

	/**
	 * The factions whose active Focus count reaches {@code threshold}.
	 *
	 * @param activeFactionIds the faction id (or {@code ""} for none) of each ACTIVE Focus
	 * @param threshold the count needed to wake a faction's set bonus
	 * @return the factions with at least {@code threshold} matching active Foci
	 */
	public static Set<String> activeSetFactions(List<String> activeFactionIds, int threshold) {
		Map<String, Integer> counts = new HashMap<>();
		for (String factionId : activeFactionIds) {
			if (factionId == null || factionId.isEmpty()) {
				continue;
			}
			counts.merge(factionId, 1, Integer::sum);
		}
		Set<String> wakened = new HashSet<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() >= threshold) {
				wakened.add(entry.getKey());
			}
		}
		return wakened;
	}
}
