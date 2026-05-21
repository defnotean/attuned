package dev.attuned.attunement;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusDefinition;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-side computation over a player's attunement state: the budget, and which
 * equipped Foci are active vs. dormant. Slot order is activation priority — the
 * lower slot index wins when the budget is tight; Foci that do not fit go dormant.
 */
public final class Attunement {
	private Attunement() {}

	public static int capacity(Player player) {
		return AttunedAttachments.getCapacity(player);
	}

	/** The {@link FocusDefinition} for a stack, or empty if the item is not a registered Focus. */
	public static Optional<FocusDefinition> definitionFor(Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		Registry<FocusDefinition> registry =
			player.level().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		for (FocusDefinition def : registry) {
			if (def.item().value() == stack.getItem()) {
				return Optional.of(def);
			}
		}
		return Optional.empty();
	}

	/** Slot indices whose Focus is active (equipped and within budget), in priority order. */
	public static List<Integer> activeSlots(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int budget = capacity(player);
		int used = 0;
		List<Integer> active = new ArrayList<>();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			Optional<FocusDefinition> def = definitionFor(player, inv.get(slot));
			if (def.isEmpty()) {
				continue;
			}
			int cost = def.get().cost();
			if (used + cost <= budget) {
				used += cost;
				active.add(slot);
			}
		}
		return active;
	}

	/** Whether the Focus in the given slot is currently active. */
	public static boolean isActive(Player player, int slot) {
		return activeSlots(player).contains(slot);
	}

	/** Total attunement points consumed by active Foci. */
	public static int used(Player player) {
		int total = 0;
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : activeSlots(player)) {
			total += definitionFor(player, inv.get(slot)).map(FocusDefinition::cost).orElse(0);
		}
		return total;
	}
}
