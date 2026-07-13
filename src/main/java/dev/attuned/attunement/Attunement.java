package dev.attuned.attunement;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.TemperingResolver;
import dev.attuned.menu.PresetMetadataResolver;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
	// ConcurrentHashMap, and server-side entries only: in singleplayer the client
	// render thread (FocusPanel/tooltips via AttunementReadout) and the server
	// thread share this static, and they see different AttunedInv instances for
	// the same player UUID — a plain HashMap would race and the entries would
	// thrash. Client lookups bypass the cache entirely.
	private static final Map<UUID, CachedResolution> RESOLUTION_CACHE = new ConcurrentHashMap<>();
	private static boolean initialized;

	static {
		AttunedPlayerCleanup.onForget(RESOLUTION_CACHE::remove);
		AttunedServerCleanup.onStop(RESOLUTION_CACHE::clear);
	}

	private Attunement() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				RESOLUTION_CACHE.clear();
			}
		});
	}

	private record CachedResolution(AttunedInv inv, int capacity, BudgetResolver.Resolution resolution) {}

	public static int capacity(Player player) {
		return AttunedAttachments.getCapacity(player);
	}

	/** The {@link FocusDefinition} for a stack, or empty if the item is not a registered Focus. */
	public static Optional<FocusDefinition> definitionFor(Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		HolderLookup.RegistryLookup<FocusDefinition> registry =
			player.level().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		return FocusLookup.forItem(registry, stack.getItem());
	}

	/** Full active/dormant resolution for the player's current Focus inventory. */
	public static BudgetResolver.Resolution resolution(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int capacity = capacity(player);
		if (player.level().isClientSide()) {
			return BudgetResolver.resolveDetailed(candidates(player, inv), capacity);
		}
		CachedResolution cached = RESOLUTION_CACHE.get(player.getUUID());
		if (cached != null && cached.inv() == inv && cached.capacity() == capacity) {
			return cached.resolution();
		}
		BudgetResolver.Resolution resolution = BudgetResolver.resolveDetailed(candidates(player, inv), capacity);
		RESOLUTION_CACHE.put(player.getUUID(), new CachedResolution(inv, capacity, resolution));
		return resolution;
	}

	/**
	 * Slot indices whose Focus is active — within budget, and (for a
	 * {@code unique} Focus) the first copy in slot order. Gathers the occupied
	 * slots and delegates the decision to the pure {@link BudgetResolver#resolve}.
	 */
	public static List<Integer> activeSlots(Player player) {
		return resolution(player).activeSlots();
	}

	/**
	 * Slot indices that would be active if {@code hypotheticalCapacity} were the
	 * player's current capacity. Used by previews; it does not mutate the player.
	 */
	public static List<Integer> activeSlots(Player player, int hypotheticalCapacity) {
		return BudgetResolver.resolve(candidates(player), hypotheticalCapacity);
	}

	private static List<BudgetResolver.Candidate<Item>> candidates(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		return candidates(player, inv);
	}

	private static List<BudgetResolver.Candidate<Item>> candidates(Player player, AttunedInv inv) {
		List<BudgetResolver.Candidate<Item>> candidates = new ArrayList<>();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			ItemStack stack = inv.get(slot);
			Optional<FocusDefinition> definition = definitionFor(player, stack);
			if (definition.isPresent()) {
				FocusDefinition def = definition.get();
				candidates.add(new BudgetResolver.Candidate<>(
					slot, effectiveCost(def, stack), def.unique(), stack.getItem()));
			}
		}
		return candidates;
	}

	/**
	 * The attunement cost a Focus stack consumes from the budget: its base
	 * {@link FocusDefinition#cost()} plus the Tempered surcharge when the stack
	 * carries the Tempered marker. Centralised here so budget resolution,
	 * dormancy reasons, and the {@link #used} readout all agree.
	 */
	public static int effectiveCost(FocusDefinition definition, ItemStack stack) {
		int base = definition.cost();
		return stack.has(AttunedComponents.TEMPERED) ? TemperingResolver.temperedCost(base) : base;
	}

	/** Dormant reasons keyed by slot for occupied Focus slots that are not active. */
	public static Map<Integer, BudgetResolver.DormantReason> dormantReasons(Player player) {
		return resolution(player).dormantReasons();
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
			ItemStack stack = inv.get(slot);
			total += definitionFor(player, stack).map(def -> effectiveCost(def, stack)).orElse(0);
		}
		return total;
	}

	/** A coarse party role label derived from active Focus metadata, never exact Focus ids. */
	public static String publicRole(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<PresetMetadataResolver.ResolvedFocus> foci = new ArrayList<>();
		for (int slot : activeSlots(player)) {
			definitionFor(player, inv.get(slot)).ifPresent(def -> foci.add(
				new PresetMetadataResolver.ResolvedFocus(
					def.affinity(), def.faction(), def.behavior().isPresent(), Optional.empty())));
		}
		return PresetMetadataResolver.inferRole(foci);
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
