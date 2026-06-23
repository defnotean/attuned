package dev.attuned.content;

import dev.attuned.Attuned;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

/**
 * The bundled Focus items shipped with Attuned. Each item's accessory behaviour
 * (cost, modifiers) lives in a datapack {@code FocusDefinition}, not here.
 */
public final class AttunedContent {
	private AttunedContent() {}

	private static final List<Item> REGISTERED_FOCI = new ArrayList<>();

	public static final Item SWIFT_FOCUS = registerFocus("swift_focus");
	public static final Item VITAL_FOCUS = registerFocus("vital_focus");
	public static final Item IRON_FOCUS = registerFocus("iron_focus");

	// Attribute Foci — behaviour is purely declarative modifiers in the datapack.
	public static final Item LEAP_FOCUS = registerFocus("leap_focus");
	public static final Item EDGE_FOCUS = registerFocus("edge_focus");
	public static final Item FRENZY_FOCUS = registerFocus("frenzy_focus");
	public static final Item CINDER_FOCUS = registerFocus("cinder_focus");
	public static final Item BULWARK_FOCUS = registerFocus("bulwark_focus");
	public static final Item DRIFT_FOCUS = registerFocus("drift_focus");

	// Behaviour Foci — datapack modifiers plus a registered code behaviour.
	public static final Item TIDE_FOCUS = registerFocus("tide_focus");
	public static final Item GALESPUR_FOCUS = registerFocus("galespur_focus");
	public static final Item RAINSTEP_FOCUS = registerFocus("rainstep_focus");
	public static final Item UPDRAFT_FOCUS = registerFocus("updraft_focus");
	public static final Item EMBERWARD_FOCUS = registerFocus("emberward_focus");
	public static final Item ANCHOR_FOCUS = registerFocus("anchor_focus");
	public static final Item AEGIS_FOCUS = registerFocus("aegis_focus");
	public static final Item NIGHTGAZE_FOCUS = registerFocus("nightgaze_focus");
	public static final Item HEARTH_FOCUS = registerFocus("hearth_focus");
	public static final Item LANTERN_FOCUS = registerFocus("lantern_focus");
	public static final Item DELVER_FOCUS = registerFocus("delver_focus");
	public static final Item LODESTONE_FOCUS = registerFocus("lodestone_focus");

	// Combat Foci — a separate combat system handles their effects.
	public static final Item THORNWARD_FOCUS = registerFocus("thornward_focus");
	public static final Item LEECH_FOCUS = registerFocus("leech_focus");

	// Expansion Foci — driven by a code behaviour, a death hook, or a teleport packet.
	public static final Item STORMCALL_FOCUS = registerFocus("stormcall_focus");
	public static final Item GRAVEBIND_FOCUS = registerFocus("gravebind_focus");
	public static final Item BLOODFURY_FOCUS = registerFocus("bloodfury_focus");
	public static final Item VOIDSTEP_FOCUS = registerFocus("voidstep_focus");
	public static final Item HARVEST_FOCUS = registerFocus("harvest_focus");
	public static final Item FORAGER_FOCUS = registerFocus("forager_focus");
	public static final Item TREMOR_FOCUS = registerFocus("tremor_focus");
	public static final Item BEACON_FOCUS = registerFocus("beacon_focus");
	public static final Item WAYSTONE_FOCUS = registerFocus("waystone_focus");

	// The Unseen — stealth-flavoured Foci that work through patience, misdirection and openings.
	public static final Item SOFTSTEP_FOCUS = registerFocus("softstep_focus");
	public static final Item VEIL_FOCUS = registerFocus("veil_focus");
	public static final Item NEEDLE_FOCUS = registerFocus("needle_focus");
	public static final Item SMOKE_FOCUS = registerFocus("smoke_focus");

	// The Seafarers - peaceful utility Foci for fishing, wayfinding, and water-side travel.
	public static final Item LINECAST_FOCUS = registerFocus("linecast_focus");
	public static final Item NETMENDER_FOCUS = registerFocus("netmender_focus");
	public static final Item HARBORLIGHT_FOCUS = registerFocus("harborlight_focus");
	public static final Item DRIFTGLASS_FOCUS = registerFocus("driftglass_focus");

	// The Offshore - dangerous water utility for salvage, storms, and things below the waves.
	public static final Item HARPOON_FOCUS = registerFocus("harpoon_focus");

	// The Radiant - Holy Foci built around vows, revelation, and measured judgment.
	public static final Item VOTIVE_FOCUS = registerFocus("votive_focus");
	public static final Item BELLWETHER_FOCUS = registerFocus("bellwether_focus");
	public static final Item OATHGUARD_FOCUS = registerFocus("oathguard_focus");
	public static final Item SUNLANCE_FOCUS = registerFocus("sunlance_focus");

	// The Reliquary - Holy utility Foci for relics, thresholds, names, and quiet rites.
	public static final Item CENSER_FOCUS = registerFocus("censer_focus");
	public static final Item NAMESAKE_FOCUS = registerFocus("namesake_focus");
	public static final Item THRESHOLD_FOCUS = registerFocus("threshold_focus");

	// The Verdant Choir - broad naturalist Foci for grounded travel and gathering.
	public static final Item ROOTSTEP_FOCUS = registerFocus("rootstep_focus");
	public static final Item BLOOM_FOCUS = registerFocus("bloom_focus");
	public static final Item MOSSHEART_FOCUS = registerFocus("mossheart_focus");

	// The Ashen Forge - craft-bound Foci with restrained Bastion/Fury utility.
	public static final Item TEMPER_FOCUS = registerFocus("temper_focus");
	public static final Item KILNWARD_FOCUS = registerFocus("kilnward_focus");
	public static final Item RIVET_FOCUS = registerFocus("rivet_focus");

	// Additional Unseen Foci - evasive and misdirection tools with low direct damage.
	public static final Item MASK_FOCUS = registerFocus("mask_focus");
	public static final Item WHISPER_FOCUS = registerFocus("whisper_focus");
	public static final Item BLACKOUT_FOCUS = registerFocus("blackout_focus");

	// The Revenant - unfinished endings, remembered deaths, debts, and grave-cold reprisals.
	public static final Item EPITAPH_FOCUS = registerFocus("epitaph_focus");
	public static final Item ASHEN_DEBT_FOCUS = registerFocus("ashen_debt_focus");
	public static final Item HOLLOWSTEP_FOCUS = registerFocus("hollowstep_focus");
	public static final Item LAST_RITES_FOCUS = registerFocus("last_rites_focus");
	public static final Item BONECHILL_FOCUS = registerFocus("bonechill_focus");

	// Umbral Eclipse - shadow Foci that wake in low light and total darkness.
	public static final Item GLOOMSTRIDE_FOCUS = registerFocus("gloomstride_focus");
	public static final Item DUSKWARD_FOCUS = registerFocus("duskward_focus");
	public static final Item SHADOWMELD_FOCUS = registerFocus("shadowmeld_focus");
	public static final Item DREADFANG_FOCUS = registerFocus("dreadfang_focus");
	public static final Item ECLIPSE_FOCUS = registerFocus("eclipse_focus");

	// Wheel of Refusals Foci - first batch beyond the original four affinities (Tide, Forge, Verdant, Umbral).
	public static final Item UNDERTOW_FOCUS = registerFocus("undertow_focus");
	public static final Item RIPTIDE_HEART_FOCUS = registerFocus("riptide_heart_focus");
	public static final Item PEARLGUARD_FOCUS = registerFocus("pearlguard_focus");
	public static final Item SLAGBRAND_FOCUS = registerFocus("slagbrand_focus");
	public static final Item ANVILHEART_FOCUS = registerFocus("anvilheart_focus");
	public static final Item SPARKWELD_FOCUS = registerFocus("sparkweld_focus");
	public static final Item THORNWAKE_FOCUS = registerFocus("thornwake_focus");
	public static final Item SEEDCALL_FOCUS = registerFocus("seedcall_focus");
	public static final Item BRAMBLEGATE_FOCUS = registerFocus("bramblegate_focus");
	public static final Item NULLVEIL_FOCUS = registerFocus("nullveil_focus");
	public static final Item CINDERTHIEF_FOCUS = registerFocus("cinderthief_focus");
	public static final Item SNAREMOON_FOCUS = registerFocus("snaremoon_focus");

	// Affinity Foci, batch one - pure-modifier Foci across the Tide, Verdant, Forge, Fury and Bastion lanes.
	public static final Item TIDEWARDEN_FOCUS = registerFocus("tidewarden_focus");
	public static final Item WELLSPRING_FOCUS = registerFocus("wellspring_focus");
	public static final Item CURRENT_RUNNER_FOCUS = registerFocus("current_runner_focus");
	public static final Item SALTBRAND_FOCUS = registerFocus("saltbrand_focus");
	public static final Item EBBSTRIDE_FOCUS = registerFocus("ebbstride_focus");
	public static final Item OVERGROWTH_FOCUS = registerFocus("overgrowth_focus");
	public static final Item DEEPROOT_FOCUS = registerFocus("deeproot_focus");
	public static final Item BRIARCOAT_FOCUS = registerFocus("briarcoat_focus");
	public static final Item FERNSTRIDE_FOCUS = registerFocus("fernstride_focus");
	public static final Item SAPFLOW_FOCUS = registerFocus("sapflow_focus");
	public static final Item CINDERPLATE_FOCUS = registerFocus("cinderplate_focus");
	public static final Item BELLOWSFURY_FOCUS = registerFocus("bellowsfury_focus");
	public static final Item BLOODRUSH_FOCUS = registerFocus("bloodrush_focus");
	public static final Item RAVAGER_FOCUS = registerFocus("ravager_focus");
	public static final Item GRANITEHIDE_FOCUS = registerFocus("granitehide_focus");
	public static final Item HAMMERWARD_FOCUS = registerFocus("hammerward_focus");

	// Deep Lanterns - cave rescue, route marking, and quiet expedition support.
	public static final Item CAVEWICK_FOCUS = registerFocus("cavewick_focus");
	public static final Item GLOWLINE_FOCUS = registerFocus("glowline_focus");
	public static final Item RESCUEFLAME_FOCUS = registerFocus("rescueflame_focus");
	public static final Item DEPTHGLASS_FOCUS = registerFocus("depthglass_focus");

	/**
	 * Blank, resource-pack-skinnable Focus items for datapack authors. Each one is a
	 * real registered Focus item with a neutral default name/model/texture, but ships
	 * <em>no</em> bundled {@code FocusDefinition} — an author points a
	 * {@code focus/<name>.json} at one and skins its name, lore, and art with a
	 * resource pack, getting a bespoke Focus identity without a JAR. They register
	 * before the {@link #FOCI} snapshot so {@link #isFocus(Item)} includes them.
	 */
	public static final List<Item> CUSTOM_FOCI = registerCustomFocusPool();

	private static List<Item> registerCustomFocusPool() {
		List<Item> pool = new ArrayList<>();
		for (int n = 1; n <= 8; n++) {
			pool.add(registerFocus("custom_focus_" + n));
		}
		return List.copyOf(pool);
	}

	/** Every Focus item, in display order for creative tabs and survival loot. */
	public static final List<Item> FOCI = List.copyOf(REGISTERED_FOCI);
	private static final Set<Item> FOCI_SET = Set.copyOf(REGISTERED_FOCI);

	/** A consumable that permanently raises attunement capacity. Stacks normally. */
	public static final Item ATTUNEMENT_SHARD = register("attunement_shard", AttunementShardItem::new);
	/** A fragment reward that smooths shard progression; four craft into one shard. */
	public static final Item ATTUNEMENT_SHARD_FRAGMENT =
		register("attunement_shard_fragment", AttunementShardFragmentItem::new);
	/** A lightweight in-game guide to Foci, affinities, Pacts and Apex. */
	public static final Item ATTUNEMENT_JOURNAL = register("attunement_journal", AttunementJournalItem::new);
	/** A portable, stack-bound holder for spare Foci. */
	public static final Item SATCHEL_OF_FOCI = register("satchel_of_foci", SatchelItem::new);
	/** A second-tier reliquary with twice the storage (54 slots) of the satchel. */
	public static final Item GRAND_SATCHEL_OF_FOCI = register("grand_satchel_of_foci", SatchelItem::grand);

	/** The Attunement Altar — the home block where shards are bound into capacity. */
	public static final Block ATTUNEMENT_ALTAR = registerAltar();
	/** The Altar of Reweaving converts three Foci and a shard fragment into a new Focus. */
	public static final Block ALTAR_OF_REWEAVING = registerReweavingAltar();

	private static Item registerFocus(String name) {
		Item item = register(name, properties -> new Item(properties.stacksTo(1)));
		REGISTERED_FOCI.add(item);
		return item;
	}

	public static boolean isFocus(Item item) {
		return item != null && FOCI_SET.contains(item);
	}

	public static boolean isFocus(ItemStack stack) {
		return !stack.isEmpty() && isFocus(stack.getItem());
	}

	/**
	 * Registers an item with a custom {@link Item} subclass. Unlike the Focus
	 * helper this does not call {@code stacksTo(1)}, so the item stacks normally.
	 */
	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(
			Registries.ITEM, new ResourceLocation(Attuned.MOD_ID, name));
		Item item = factory.apply(new Item.Properties());
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Registers the Attunement Altar block and its matching block item. */
	private static Block registerAltar() {
		ResourceLocation id = new ResourceLocation(Attuned.MOD_ID, "attunement_altar");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = new AttunementAltarBlock(BlockBehaviour.Properties.of(Material.STONE)
			.strength(3.5F, 6.0F)
			.sound(SoundType.DEEPSLATE)
			.lightLevel(state -> 7)
			.noOcclusion());
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(block, new Item.Properties()));
		return block;
	}

	/** Registers the Altar of Reweaving block and its matching block item. */
	private static Block registerReweavingAltar() {
		ResourceLocation id = new ResourceLocation(Attuned.MOD_ID, "altar_of_reweaving");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = new AltarOfReweavingBlock(BlockBehaviour.Properties.of(Material.STONE)
			.strength(3.5F, 6.0F)
			.sound(SoundType.DEEPSLATE)
			.lightLevel(state -> 6)
			.noOcclusion());
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(block, new Item.Properties()));
		return block;
	}

	/**
	 * Forces this class to load so the items register, registers Focus
	 * behaviours, and registers the creative tabs.
	 */
	public static void init() {
		AttunedFocusBehaviors.init();
		AttunedCreativeTabs.init();
	}
}
