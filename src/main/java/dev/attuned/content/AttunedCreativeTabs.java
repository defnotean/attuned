package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registers and orders Attuned creative-inventory tabs. */
final class AttunedCreativeTabs {
	private static boolean initialized;

	private AttunedCreativeTabs() {}

	/**
	 * Registers the Attuned creative-inventory tabs so Foci and altar tools are
	 * reachable without {@code /give}.
	 *
	 * <p>Foci are grouped by affinity (Fury, Bastion, Zephyr, Holy, then neutral)
	 * and sorted within each group by attunement cost, so players can scan the
	 * tabs by build identity rather than registration order.
	 */
	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		registerFocusCreativeTab(
			"attuned",
			Component.translatable("itemGroup.attuned.affinity_foci"),
			AttunedContent.SUNLANCE_FOCUS,
			definition -> definition != null && definition.affinity().isPresent(),
			false);
		registerFocusCreativeTab(
			"attuned_utility",
			Component.translatable("itemGroup.attuned.utility_foci"),
			AttunedContent.LINECAST_FOCUS,
			definition -> definition == null || definition.affinity().isEmpty(),
			true);
	}

	private static void registerFocusCreativeTab(String id, Component title, Item icon,
			Predicate<FocusDefinition> include, boolean includeCoreItems) {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(title)
			.icon(() -> new ItemStack(icon))
			.displayItems((parameters, output) -> {
				HolderLookup.RegistryLookup<FocusDefinition> lookup =
					parameters.holders().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
				for (Item focus : fociInDisplayOrder(lookup, include)) {
					output.accept(focus);
				}
				if (includeCoreItems) {
					output.accept(AttunedContent.ATTUNEMENT_SHARD);
					output.accept(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
					output.accept(AttunedContent.ATTUNEMENT_JOURNAL);
					output.accept(AttunedContent.ATTUNEMENT_ALTAR);
					output.accept(AttunedContent.ALTAR_OF_REWEAVING);
				}
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, id), tab);
	}

	/**
	 * Returns the Foci in display order for a creative tab: grouped by
	 * affinity (Fury, then Bastion, then Zephyr, then Holy, then neutral), and sorted
	 * within each group by attunement cost ascending, then by registry id
	 * alphabetically as a tiebreak. A Focus whose definition is missing from
	 * the supplied lookup falls into the neutral group at the maximum cost so
	 * it sorts to the very end.
	 */
	private static List<Item> fociInDisplayOrder(HolderLookup.RegistryLookup<FocusDefinition> lookup,
			Predicate<FocusDefinition> include) {
		Map<Item, FocusDefinition> byItem = new IdentityHashMap<>();
		lookup.listElements().forEach(holder -> byItem.put(holder.value().item().value(), holder.value()));
		Comparator<Item> byAffinity = Comparator.comparingInt(item -> {
			FocusDefinition def = byItem.get(item);
			return affinityOrder(def == null ? Optional.empty() : def.affinity());
		});
		Comparator<Item> byCost = Comparator.comparingInt(item -> {
			FocusDefinition def = byItem.get(item);
			return def == null ? Integer.MAX_VALUE : def.cost();
		});
		Comparator<Item> byKey = Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString());
		List<Item> sorted = new ArrayList<>();
		for (Item item : AttunedContent.FOCI) {
			FocusDefinition definition = byItem.get(item);
			if (include.test(definition)) {
				sorted.add(item);
			}
		}
		sorted.sort(byAffinity.thenComparing(byCost).thenComparing(byKey));
		return sorted;
	}

	/**
	 * Stable sort key for the affinity grouping: Fury, Bastion, Zephyr, Holy, then
	 * affinity-neutral last.
	 */
	private static int affinityOrder(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return 4;
		}
		return switch (affinity.get()) {
			case FURY -> 0;
			case BASTION -> 1;
			case ZEPHYR -> 2;
			case HOLY -> 3;
		};
	}
}
