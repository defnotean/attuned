package dev.attuned.menu;

import dev.attuned.attunement.FocusPreset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure availability logic for the Reliquary's build hover preview. Decides, per
 * build slot, whether the saved Focus id can be sourced for an Apply.
 *
 * <p>The sourcing rule mirrors {@link PresetApplicationResolver#applyAllOrNothing}:
 * copies are resolved across the player's equipped Foci, the reliquary contents,
 * and loose inventory stacks. If the saved build cannot fully apply, every
 * requested Focus slot is {@link Availability#MISSING}. Empty-string slots are
 * always {@link Availability#OWNED}.
 */
public final class BuildPreviewResolver {
	private BuildPreviewResolver() {}

	public enum Availability {
		OWNED,
		MISSING
	}

	public static List<Availability> availability(List<String> buildSlots, List<String> equippedIds,
			List<String> satchelIds, Map<String, Integer> inventoryCounts) {
		return availability(buildSlots, equippedIds, satchelIds, inventoryCounts,
			registeredIds(buildSlots, equippedIds, satchelIds, inventoryCounts));
	}

	public static List<Availability> availability(List<String> buildSlots, List<String> equippedIds,
			List<String> satchelIds, Map<String, Integer> inventoryCounts, Set<String> registeredFocusIds) {
		List<Availability> result = new ArrayList<>(buildSlots == null ? 0 : buildSlots.size());
		if (buildSlots == null) {
			return result;
		}
		PresetApplicationResolver.Result apply = PresetApplicationResolver.applyAllOrNothing(
			buildSlots, equippedIds, satchelIds, inventoryCounts, normalizedRegisteredIds(registeredFocusIds));
		for (int slot = 0; slot < buildSlots.size(); slot++) {
			String id = normalize(buildSlots.get(slot));
			if (id.isEmpty()) {
				result.add(Availability.OWNED);
				continue;
			}
			if (apply.missing().isEmpty() && slot < apply.equips().size() && id.equals(apply.equips().get(slot))) {
				result.add(Availability.OWNED);
			} else {
				result.add(Availability.MISSING);
			}
		}
		return result;
	}

	private static Set<String> normalizedRegisteredIds(Set<String> registeredFocusIds) {
		Set<String> ids = new HashSet<>();
		if (registeredFocusIds == null) {
			return ids;
		}
		for (String id : registeredFocusIds) {
			String normalized = normalize(id);
			if (!normalized.isEmpty()) {
				ids.add(FocusPreset.slotId(normalized));
			}
		}
		return ids;
	}

	private static Set<String> registeredIds(List<String> buildSlots, List<String> equippedIds, List<String> satchelIds,
			Map<String, Integer> inventoryCounts) {
		Set<String> ids = new HashSet<>();
		addRegisteredIds(ids, buildSlots);
		addRegisteredIds(ids, equippedIds);
		addRegisteredIds(ids, satchelIds);
		if (inventoryCounts != null) {
			addRegisteredIds(ids, inventoryCounts.keySet().stream().toList());
		}
		return ids;
	}

	private static void addRegisteredIds(Set<String> registered, List<String> ids) {
		if (ids == null) {
			return;
		}
		for (String id : ids) {
			String normalized = normalize(id);
			if (!normalized.isEmpty()) {
				registered.add(FocusPreset.slotId(normalized));
			}
		}
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim();
	}
}
