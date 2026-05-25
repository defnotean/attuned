package dev.attuned.attunement;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-side computation over a player's attunement state: the budget, which
 * equipped Foci are active vs. dormant, and the affinity stance that follows.
 *
 * <p>Slot order is activation priority. A Focus is active when it is within the
 * attunement budget — affinity does not gate activation. The affinities of the
 * active Foci then decide the player's stance: no affinity is unattuned, one
 * affinity is a committed lane, and two or more is Discord.
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
	 * Slot indices whose Focus is active — within budget, and (for a
	 * {@code unique} Focus) the first copy in slot order. Gathers the occupied
	 * slots and delegates the decision to the pure {@link BudgetResolver#resolve}.
	 */
	public static List<Integer> activeSlots(Player player) {
		return BudgetResolver.resolve(candidates(player), capacity(player));
	}

	private static List<BudgetResolver.Candidate<Item>> candidates(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<BudgetResolver.Candidate<Item>> candidates = new ArrayList<>();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			ItemStack stack = inv.get(slot);
			Optional<FocusDefinition> definition = definitionFor(player, stack);
			if (definition.isPresent()) {
				FocusDefinition def = definition.get();
				candidates.add(new BudgetResolver.Candidate<>(
					slot, def.cost(), def.unique(), stack.getItem()));
			}
		}
		return candidates;
	}

	/** Dormant reasons keyed by slot for occupied Focus slots that are not active. */
	public static Map<Integer, BudgetResolver.DormantReason> dormantReasons(Player player) {
		return BudgetResolver.resolveDetailed(candidates(player), capacity(player)).dormantReasons();
	}

	/** Why the Focus in the given slot is dormant, if it is currently dormant. */
	public static Optional<BudgetResolver.DormantReason> dormantReason(Player player, int slot) {
		return Optional.ofNullable(dormantReasons(player).get(slot));
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

	/** The distinct affinities carried by the player's active Foci. */
	public static Set<Affinity> activeAffinities(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		Set<Affinity> affinities = EnumSet.noneOf(Affinity.class);
		for (int slot : activeSlots(player)) {
			definitionFor(player, inv.get(slot))
				.flatMap(FocusDefinition::affinity)
				.ifPresent(affinities::add);
		}
		return affinities;
	}

	/**
	 * The single affinity the player is committed to — present only when every
	 * affinity-bearing active Focus shares it. Empty when unattuned or in Discord.
	 */
	public static Optional<Affinity> committedAffinity(Player player) {
		Set<Affinity> affinities = activeAffinities(player);
		return affinities.size() == 1 ? Optional.of(affinities.iterator().next()) : Optional.empty();
	}

	/** Whether the player's active Foci span two or more affinities — the Discord stance. */
	public static boolean isDiscord(Player player) {
		return activeAffinities(player).size() >= 2;
	}
}
