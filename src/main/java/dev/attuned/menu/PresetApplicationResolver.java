package dev.attuned.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure preset application logic over item registry id strings. */
public final class PresetApplicationResolver {
	private static final int SLOT_COUNT = 6;

	private PresetApplicationResolver() {}

	public record Result(List<String> equips, List<String> satchel, Map<String, Integer> consumedInventory,
			List<String> missing) {
		public Result {
			equips = List.copyOf(equips);
			satchel = List.copyOf(satchel);
			consumedInventory = Collections.unmodifiableMap(new LinkedHashMap<>(consumedInventory));
			missing = List.copyOf(missing);
		}
	}

	public static Result apply(List<String> preset, List<String> equippedNow, List<String> satchel,
			Map<String, Integer> inventoryFocusCounts, Set<String> registeredFocusIds) {
		List<String> target = sizedSlots(preset);
		List<String> equipped = sizedSlots(equippedNow);
		int satchelSize = satchel == null ? 0 : satchel.size();
		Map<String, Integer> consumedInventory = new LinkedHashMap<>();
		Set<String> registered = registeredFocusIds == null ? Set.of() : registeredFocusIds;
		List<String> residualSatchel = registeredSatchelSlots(satchel, satchelSize, registered);
		List<String> displacedEquipped = displacedEquipped(equipped, target, registered);
		Map<String, Integer> inventory = positiveRegisteredCounts(inventoryFocusCounts, registered);

		List<String> equips = new ArrayList<>(SLOT_COUNT);
		List<String> missing = new ArrayList<>();
		for (int slot = 0; slot < target.size(); slot++) {
			String requested = target.get(slot);
			if (requested.isEmpty()) {
				equips.add("");
				continue;
			}
			if (!registered.contains(requested)) {
				missing.add(requested);
				equips.add("");
				continue;
			}
			if (requested.equals(equipped.get(slot))
					|| consumeFromSatchel(residualSatchel, requested)
					|| consumeFromPool(displacedEquipped, requested)
					|| consumeFromInventory(inventory, consumedInventory, requested)) {
				equips.add(requested);
			} else {
				missing.add(requested);
				equips.add("");
			}
		}

		placeDisplacedInSatchel(residualSatchel, displacedEquipped);
		return new Result(equips, residualSatchel, consumedInventory, missing);
	}

	private static List<String> sizedSlots(List<String> source) {
		List<String> slots = new ArrayList<>(SLOT_COUNT);
		int sourceSize = source == null ? 0 : source.size();
		for (int i = 0; i < SLOT_COUNT; i++) {
			String id = i < sourceSize ? source.get(i) : "";
			slots.add(normalize(id));
		}
		return slots;
	}

	private static List<String> registeredSatchelSlots(List<String> source, int satchelSize, Set<String> registered) {
		List<String> ids = new ArrayList<>(satchelSize);
		for (int i = 0; i < satchelSize; i++) {
			String normalized = normalize(source == null || i >= source.size() ? "" : source.get(i));
			ids.add(normalized);
		}
		return ids;
	}

	private static List<String> displacedEquipped(List<String> equipped, List<String> target, Set<String> registered) {
		List<String> displaced = new ArrayList<>();
		for (int slot = 0; slot < equipped.size(); slot++) {
			String id = equipped.get(slot);
			String targetId = slot < target.size() ? target.get(slot) : "";
			if (!id.isEmpty() && (!id.equals(targetId) || !registered.contains(id))) {
				displaced.add(id);
			}
		}
		return displaced;
	}

	private static Map<String, Integer> positiveRegisteredCounts(Map<String, Integer> source,
			Set<String> registered) {
		Map<String, Integer> counts = new HashMap<>();
		if (source == null) {
			return counts;
		}
		for (Map.Entry<String, Integer> entry : source.entrySet()) {
			String id = normalize(entry.getKey());
			int count = entry.getValue() == null ? 0 : entry.getValue();
			if (!id.isEmpty() && count > 0 && registered.contains(id)) {
				counts.put(id, count);
			}
		}
		return counts;
	}

	private static boolean consumeFromSatchel(List<String> satchelPool, String id) {
		for (int i = 0; i < satchelPool.size(); i++) {
			if (id.equals(satchelPool.get(i))) {
				satchelPool.set(i, "");
				return true;
			}
		}
		return false;
	}

	private static boolean consumeFromPool(List<String> pool, String id) {
		for (int i = 0; i < pool.size(); i++) {
			if (id.equals(pool.get(i))) {
				pool.remove(i);
				return true;
			}
		}
		return false;
	}

	private static void placeDisplacedInSatchel(List<String> residualSatchel, List<String> displacedEquipped) {
		while (!displacedEquipped.isEmpty()) {
			int freeSlot = residualSatchel.indexOf("");
			if (freeSlot < 0) {
				return;
			}
			residualSatchel.set(freeSlot, displacedEquipped.remove(0));
		}
	}

	private static boolean consumeFromInventory(Map<String, Integer> inventory,
			Map<String, Integer> consumedInventory, String id) {
		int count = inventory.getOrDefault(id, 0);
		if (count <= 0) {
			return false;
		}
		if (count == 1) {
			inventory.remove(id);
		} else {
			inventory.put(id, count - 1);
		}
		consumedInventory.merge(id, 1, Integer::sum);
		return true;
	}

	private static String normalize(String id) {
		return id == null ? "" : id.trim();
	}
}
