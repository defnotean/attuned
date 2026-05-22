package dev.attuned.attunement;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
		return FocusLookup.forItem(registry, stack.getItem());
	}

	/**
	 * Slot indices whose Focus is active — within budget, on the committed
	 * affinity, and (for a {@code unique} Focus) the first copy in slot order.
	 * Gathers the occupied slots and delegates the decision to the pure
	 * {@link BudgetResolver#resolve}.
	 */
	public static List<Integer> activeSlots(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<BudgetResolver.Candidate<Affinity, Item>> candidates = new ArrayList<>();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			ItemStack stack = inv.get(slot);
			Optional<FocusDefinition> definition = definitionFor(player, stack);
			if (definition.isPresent()) {
				FocusDefinition def = definition.get();
				candidates.add(new BudgetResolver.Candidate<>(
					slot, def.cost(), def.affinity().orElse(null), def.unique(), stack.getItem()));
			}
		}
		return BudgetResolver.resolve(candidates, capacity(player));
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
