package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.content.behavior.AegisBehavior;
import dev.attuned.content.behavior.AnchorBehavior;
import dev.attuned.content.behavior.BeaconBehavior;
import dev.attuned.content.behavior.BloodfuryBehavior;
import dev.attuned.content.behavior.DelverBehavior;
import dev.attuned.content.behavior.EmberwardBehavior;
import dev.attuned.content.behavior.GalespurBehavior;
import dev.attuned.content.behavior.HarvestBehavior;
import dev.attuned.content.behavior.HearthBehavior;
import dev.attuned.content.behavior.LanternBehavior;
import dev.attuned.content.behavior.LodestoneBehavior;
import dev.attuned.content.behavior.NightgazeBehavior;
import dev.attuned.content.behavior.RainstepBehavior;
import dev.attuned.content.behavior.SmokeBehavior;
import dev.attuned.content.behavior.SoftstepBehavior;
import dev.attuned.content.behavior.StormcallBehavior;
import dev.attuned.content.behavior.TideBehavior;
import dev.attuned.content.behavior.VeilBehavior;
import dev.attuned.content.behavior.VoidstepBehavior;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The bundled Focus items shipped with Attuned. Each item's accessory behaviour
 * (cost, modifiers) lives in a datapack {@code FocusDefinition}, not here.
 */
public final class AttunedContent {
	private AttunedContent() {}

	public static final Item SWIFT_FOCUS = register("swift_focus");
	public static final Item VITAL_FOCUS = register("vital_focus");
	public static final Item IRON_FOCUS = register("iron_focus");

	// Attribute Foci — behaviour is purely declarative modifiers in the datapack.
	public static final Item LEAP_FOCUS = register("leap_focus");
	public static final Item EDGE_FOCUS = register("edge_focus");
	public static final Item FRENZY_FOCUS = register("frenzy_focus");
	public static final Item BULWARK_FOCUS = register("bulwark_focus");
	public static final Item DRIFT_FOCUS = register("drift_focus");

	// Behaviour Foci — datapack modifiers plus a registered code behaviour.
	public static final Item TIDE_FOCUS = register("tide_focus");
	public static final Item GALESPUR_FOCUS = register("galespur_focus");
	public static final Item RAINSTEP_FOCUS = register("rainstep_focus");
	public static final Item EMBERWARD_FOCUS = register("emberward_focus");
	public static final Item ANCHOR_FOCUS = register("anchor_focus");
	public static final Item AEGIS_FOCUS = register("aegis_focus");
	public static final Item NIGHTGAZE_FOCUS = register("nightgaze_focus");
	public static final Item HEARTH_FOCUS = register("hearth_focus");
	public static final Item LANTERN_FOCUS = register("lantern_focus");
	public static final Item DELVER_FOCUS = register("delver_focus");
	public static final Item LODESTONE_FOCUS = register("lodestone_focus");

	// Combat Foci — a separate combat system handles their effects.
	public static final Item THORNWARD_FOCUS = register("thornward_focus");
	public static final Item LEECH_FOCUS = register("leech_focus");

	// Expansion Foci — driven by a code behaviour, a death hook, or a teleport packet.
	public static final Item STORMCALL_FOCUS = register("stormcall_focus");
	public static final Item GRAVEBIND_FOCUS = register("gravebind_focus");
	public static final Item BLOODFURY_FOCUS = register("bloodfury_focus");
	public static final Item VOIDSTEP_FOCUS = register("voidstep_focus");
	public static final Item HARVEST_FOCUS = register("harvest_focus");
	public static final Item BEACON_FOCUS = register("beacon_focus");

	// The Unseen — stealth-flavoured Foci that work through patience, misdirection and openings.
	public static final Item SOFTSTEP_FOCUS = register("softstep_focus");
	public static final Item VEIL_FOCUS = register("veil_focus");
	public static final Item NEEDLE_FOCUS = register("needle_focus");
	public static final Item SMOKE_FOCUS = register("smoke_focus");

	/** A consumable that permanently raises attunement capacity. Stacks normally. */
	public static final Item ATTUNEMENT_SHARD = register("attunement_shard", AttunementShardItem::new);
	/** A fragment reward that smooths shard progression; four craft into one shard. */
	public static final Item ATTUNEMENT_SHARD_FRAGMENT =
		register("attunement_shard_fragment", AttunementShardFragmentItem::new);
	/** A lightweight in-game guide to Foci, affinities, Pacts and Apex. */
	public static final Item ATTUNEMENT_JOURNAL = register("attunement_journal", AttunementJournalItem::new);

	/** The Attunement Altar — the home block where shards are bound into capacity. */
	public static final Block ATTUNEMENT_ALTAR = registerAltar();

	/** Every Focus item, in display order — the single source for the creative tab and survival loot. */
	public static final List<Item> FOCI = List.of(
		SWIFT_FOCUS, VITAL_FOCUS, IRON_FOCUS, LEAP_FOCUS, EDGE_FOCUS, FRENZY_FOCUS,
		BULWARK_FOCUS, DRIFT_FOCUS, TIDE_FOCUS, EMBERWARD_FOCUS, AEGIS_FOCUS,
		GALESPUR_FOCUS, RAINSTEP_FOCUS, ANCHOR_FOCUS, NIGHTGAZE_FOCUS, HEARTH_FOCUS,
		LANTERN_FOCUS, DELVER_FOCUS, LODESTONE_FOCUS, THORNWARD_FOCUS,
		LEECH_FOCUS, STORMCALL_FOCUS, GRAVEBIND_FOCUS, BLOODFURY_FOCUS, VOIDSTEP_FOCUS,
		HARVEST_FOCUS, BEACON_FOCUS, SOFTSTEP_FOCUS, VEIL_FOCUS, NEEDLE_FOCUS, SMOKE_FOCUS);

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(
			Registries.ITEM, Identifier.fromNamespaceAndPath(Attuned.MOD_ID, name));
		Item item = new Item(new Item.Properties().setId(key).stacksTo(1));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/**
	 * Registers an item with a custom {@link Item} subclass. Unlike the Focus
	 * helper this does not call {@code stacksTo(1)}, so the item stacks normally.
	 */
	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(
			Registries.ITEM, Identifier.fromNamespaceAndPath(Attuned.MOD_ID, name));
		Item item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Registers the Attunement Altar block and its matching block item. */
	private static Block registerAltar() {
		Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "attunement_altar");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = new AttunementAltarBlock(BlockBehaviour.Properties.of()
			.setId(blockKey)
			.strength(3.5F, 6.0F)
			.sound(SoundType.DEEPSLATE)
			.lightLevel(state -> 7)
			.noOcclusion());
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(block, new Item.Properties().setId(itemKey)));
		return block;
	}

	/**
	 * Forces this class to load so the items register, registers Focus
	 * behaviours, and registers the creative tab.
	 */
	public static void init() {
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "tide"), new TideBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "galespur"), new GalespurBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "rainstep"), new RainstepBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "emberward"), new EmberwardBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "anchor"), new AnchorBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "aegis"), new AegisBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "nightgaze"), new NightgazeBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hearth"), new HearthBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "lantern"), new LanternBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "delver"), new DelverBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "lodestone"), new LodestoneBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "stormcall"), new StormcallBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "bloodfury"), new BloodfuryBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "harvest"), new HarvestBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "beacon"), new BeaconBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "voidstep"), new VoidstepBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "softstep"), new SoftstepBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "veil"), new VeilBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "smoke"), new SmokeBehavior());
		registerCreativeTab();
	}

	/**
	 * Registers the Attuned creative-inventory tab — every Focus and the
	 * Attunement Shard, so the mod's items are reachable without {@code /give}.
	 *
	 * <p>Foci are grouped by affinity (Fury, Bastion, Zephyr, then neutral)
	 * and sorted within each group by attunement cost, so players can scan the
	 * tab by build identity rather than registration order.
	 */
	private static void registerCreativeTab() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.attuned"))
			.icon(() -> new ItemStack(ATTUNEMENT_SHARD))
			.displayItems((parameters, output) -> {
				HolderLookup.RegistryLookup<FocusDefinition> lookup =
					parameters.holders().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
				for (Item focus : fociInDisplayOrder(lookup)) {
					output.accept(focus);
				}
				output.accept(ATTUNEMENT_SHARD);
				output.accept(ATTUNEMENT_SHARD_FRAGMENT);
				output.accept(ATTUNEMENT_JOURNAL);
				output.accept(ATTUNEMENT_ALTAR);
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "attuned"), tab);
	}

	/**
	 * Returns the Foci in display order for the creative tab: grouped by
	 * affinity (Fury, then Bastion, then Zephyr, then neutral), and sorted
	 * within each group by attunement cost ascending, then by registry id
	 * alphabetically as a tiebreak. A Focus whose definition is missing from
	 * the supplied lookup falls into the neutral group at the maximum cost so
	 * it sorts to the very end.
	 */
	private static List<Item> fociInDisplayOrder(HolderLookup.RegistryLookup<FocusDefinition> lookup) {
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
		List<Item> sorted = new ArrayList<>(FOCI);
		sorted.sort(byAffinity.thenComparing(byCost).thenComparing(byKey));
		return sorted;
	}

	/**
	 * Stable sort key for the affinity grouping: Fury, Bastion, Zephyr, then
	 * affinity-neutral last.
	 */
	private static int affinityOrder(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return 3;
		}
		return switch (affinity.get()) {
			case FURY -> 0;
			case BASTION -> 1;
			case ZEPHYR -> 2;
		};
	}
}
