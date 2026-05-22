package dev.attuned.attunement;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-side computation over a player's attunement state: the budget, the
 * committed affinity, and which equipped Foci are active vs. dormant.
 *
 * <p>Slot order is activation priority. A Focus is active when it is within the
 * attunement budget AND its affinity matches the player's committed affinity —
 * the affinity of the first affinity-bearing Focus to activate. Affinity-neutral
 * Foci are eligible regardless of the committed affinity.
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

	/** Slot indices whose Focus is active — within budget and on the committed affinity. */
	public static List<Integer> activeSlots(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int budget = capacity(player);
		int used = 0;
		Affinity committed = null;
		List<Integer> active = new ArrayList<>();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			Optional<FocusDefinition> definition = definitionFor(player, inv.get(slot));
			if (definition.isEmpty()) {
				continue;
			}
			FocusDefinition def = definition.get();
			Optional<Affinity> affinity = def.affinity();
			// Affinity restriction: once committed, a mismatched affinity stays dormant.
			if (affinity.isPresent() && committed != null && committed != affinity.get()) {
				continue;
			}
			if (used + def.cost() <= budget) {
				used += def.cost();
				active.add(slot);
				if (affinity.isPresent() && committed == null) {
					committed = affinity.get();
				}
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

	/** The affinity the player is currently committed to, if any active Focus carries one. */
	public static Optional<Affinity> committedAffinity(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : activeSlots(player)) {
			Optional<Affinity> affinity =
				definitionFor(player, inv.get(slot)).flatMap(FocusDefinition::affinity);
			if (affinity.isPresent()) {
				return affinity;
			}
		}
		return Optional.empty();
	}
}
