package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
	 * <p>The 1.5.0 Focus roster is large enough that one affinity tab becomes a
	 * wall of icons. Split the eight lanes into readable pairs, then keep neutral
	 * utility Foci, author placeholders, and core tools on the utility tab.
	 */
	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		registerFocusCreativeTab(
			"attuned",
			Component.translatable("itemGroup.attuned.fury_bastion_foci"),
			AttunedContent.BLOODFURY_FOCUS,
			definition -> hasAnyAffinity(definition, Set.of(Affinity.FURY, Affinity.BASTION)),
			false);
		registerFocusCreativeTab(
			"attuned_zephyr_holy",
			Component.translatable("itemGroup.attuned.zephyr_holy_foci"),
			AttunedContent.GALESPUR_FOCUS,
			definition -> hasAnyAffinity(definition, Set.of(Affinity.ZEPHYR, Affinity.HOLY)),
			false);
		registerFocusCreativeTab(
			"attuned_tide_forge",
			Component.translatable("itemGroup.attuned.tide_forge_foci"),
			AttunedContent.TIDEWARDEN_FOCUS,
			definition -> hasAnyAffinity(definition, Set.of(Affinity.TIDE, Affinity.FORGE)),
			false);
		registerFocusCreativeTab(
			"attuned_verdant_umbral",
			Component.translatable("itemGroup.attuned.verdant_umbral_foci"),
			AttunedContent.OVERGROWTH_FOCUS,
			definition -> hasAnyAffinity(definition, Set.of(Affinity.VERDANT, Affinity.UMBRAL)),
			false);
		registerFocusCreativeTab(
			"attuned_utility",
			Component.translatable("itemGroup.attuned.utility_foci"),
			AttunedContent.LINECAST_FOCUS,
			definition -> definition.affinity().isEmpty(),
			true);
	}

	private static void registerFocusCreativeTab(String id, Component title, Item icon,
			Predicate<FocusDefinition> include, boolean includeCoreItems) {
		CreativeModeTab tab = FabricItemGroup.builder()
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
					output.accept(AttunedContent.SATCHEL_OF_FOCI);
					output.accept(AttunedContent.GRAND_SATCHEL_OF_FOCI);
					output.accept(AttunedContent.ATTUNEMENT_ALTAR);
					output.accept(AttunedContent.ALTAR_OF_REWEAVING);
					// The blank, author-skinnable Focus pool carries no FocusDefinition,
					// so fociInDisplayOrder never surfaces it — accept it explicitly.
					for (Item customFocus : AttunedContent.CUSTOM_FOCI) {
						output.accept(customFocus);
					}
				}
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, id), tab);
	}

	/**
	 * Returns the Foci in display order for a creative tab: grouped by affinity,
	 * then by named content family, then by attunement cost, then by registry id.
	 */
	private static List<Item> fociInDisplayOrder(HolderLookup.RegistryLookup<FocusDefinition> lookup,
			Predicate<FocusDefinition> include) {
		Comparator<FocusDefinition> byAffinity =
			Comparator.comparingInt(definition -> affinityOrder(definition.affinity()));
		Comparator<FocusDefinition> byFaction =
			Comparator.comparing(definition -> factionKey(definition.faction()));
		Comparator<FocusDefinition> byCost = Comparator.comparingInt(FocusDefinition::cost);
		Comparator<FocusDefinition> byKey =
			Comparator.comparing(definition -> BuiltInRegistries.ITEM.getKey(definition.item().value()).toString());
		return lookup.listElements()
			.map(holder -> holder.value())
			.filter(include)
			.sorted(byAffinity.thenComparing(byFaction).thenComparing(byCost).thenComparing(byKey))
			.map(definition -> definition.item().value())
			.toList();
	}

	private static boolean hasAnyAffinity(FocusDefinition definition, Set<Affinity> affinities) {
		return definition.affinity().filter(affinities::contains).isPresent();
	}

	private static String factionKey(Optional<ResourceLocation> faction) {
		return faction.map(ResourceLocation::toString).orElse("");
	}

	/**
	 * Stable sort key for the affinity grouping: Fury, Bastion, Zephyr, Holy,
	 * Tide, Forge, Verdant, Umbral, then affinity-neutral last.
	 */
	private static int affinityOrder(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return 8;
		}
		return switch (affinity.get()) {
			case FURY -> 0;
			case BASTION -> 1;
			case ZEPHYR -> 2;
			case HOLY -> 3;
			case TIDE -> 4;
			case FORGE -> 5;
			case VERDANT -> 6;
			case UMBRAL -> 7;
		};
	}
}
