package dev.attuned.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure availability logic for the Reliquary's build hover preview. Decides, per
 * build slot, whether the saved Focus id can be sourced for an Apply.
 *
 * <p>The sourcing rule mirrors {@link PresetApplicationResolver}: copies are pooled
 * across the player's equipped Foci, the reliquary contents, and loose inventory
 * stacks, and consumed with multiset semantics. Two slots requesting the same id
 * therefore need two copies; the second is {@link Availability#MISSING} when only
 * one copy exists. Empty-string slots are always {@link Availability#OWNED}.
 */
public final class BuildPreviewResolver {
	private BuildPreviewResolver() {}

	public enum Availability {
		OWNED,
		MISSING
	}

	public static List<Availability> availability(List<String> buildSlots, List<String> equippedIds,
			List<String> satchelIds, Map<String, Integer> inventoryCounts) {
		Map<String, Integer> pool = pooledCounts(equippedIds, satchelIds, inventoryCounts);
		List<Availability> result = new ArrayList<>(buildSlots == null ? 0 : buildSlots.size());
		if (buildSlots == null) {
			return result;
		}
		for (String rawSlot : buildSlots) {
			String id = normalize(rawSlot);
			if (id.isEmpty()) {
				result.add(Availability.OWNED);
				continue;
			}
			int remaining = pool.getOrDefault(id, 0);
			if (remaining > 0) {
				pool.put(id, remaining - 1);
				result.add(Availability.OWNED);
			} else {
				result.add(Availability.MISSING);
			}
		}
		return result;
	}

	private static Map<String, Integer> pooledCounts(List<String> equippedIds, List<String> satchelIds,
			Map<String, Integer> inventoryCounts) {
		Map<String, Integer> pool = new HashMap<>();
		addList(pool, equippedIds);
		addList(pool, satchelIds);
		if (inventoryCounts != null) {
			for (Map.Entry<String, Integer> entry : inventoryCounts.entrySet()) {
				String id = normalize(entry.getKey());
				int count = entry.getValue() == null ? 0 : entry.getValue();
				if (!id.isEmpty() && count > 0) {
					pool.merge(id, count, Integer::sum);
				}
			}
		}
		return pool;
	}

	private static void addList(Map<String, Integer> pool, List<String> ids) {
		if (ids == null) {
			return;
		}
		for (String raw : ids) {
			String id = normalize(raw);
			if (!id.isEmpty()) {
				pool.merge(id, 1, Integer::sum);
			}
		}
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim();
	}
}
