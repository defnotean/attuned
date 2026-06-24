package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.platform.ForgeRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
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

	private static final List<Item> REGISTERED_FOCI = new ArrayList<>();
	private static final List<Item> REGISTERED_CUSTOM_FOCI = new ArrayList<>();
	private static boolean initialized;

	/**
	 * Blank, resource-pack-skinnable Focus items for datapack authors. Each one is a
	 * real registered Focus item with a neutral default name/model/texture, but ships
	 * <em>no</em> bundled {@code FocusDefinition}. An author points a
	 * {@code focus/<name>.json} at one and skins its name, lore, and art with a
	 * resource pack, getting a bespoke Focus identity without a JAR.
	 */
	public static final List<Item> CUSTOM_FOCI = Collections.unmodifiableList(REGISTERED_CUSTOM_FOCI);
	/** Every Focus item, in display order for creative tabs and survival loot. */
	public static final List<Item> FOCI = Collections.unmodifiableList(REGISTERED_FOCI);

	public static Item SWIFT_FOCUS;
	public static Item VITAL_FOCUS;
	public static Item IRON_FOCUS;

	// Attribute Foci - behaviour is purely declarative modifiers in the datapack.
	public static Item LEAP_FOCUS;
	public static Item EDGE_FOCUS;
	public static Item FRENZY_FOCUS;
	public static Item CINDER_FOCUS;
	public static Item BULWARK_FOCUS;
	public static Item DRIFT_FOCUS;

	// Behaviour Foci - datapack modifiers plus a registered code behaviour.
	public static Item TIDE_FOCUS;
	public static Item GALESPUR_FOCUS;
	public static Item RAINSTEP_FOCUS;
	public static Item UPDRAFT_FOCUS;
	public static Item EMBERWARD_FOCUS;
	public static Item ANCHOR_FOCUS;
	public static Item AEGIS_FOCUS;
	public static Item NIGHTGAZE_FOCUS;
	public static Item HEARTH_FOCUS;
	public static Item LANTERN_FOCUS;
	public static Item DELVER_FOCUS;
	public static Item LODESTONE_FOCUS;

	// Combat Foci - a separate combat system handles their effects.
	public static Item THORNWARD_FOCUS;
	public static Item LEECH_FOCUS;

	// Expansion Foci - driven by a code behaviour, a death hook, or a teleport packet.
	public static Item STORMCALL_FOCUS;
	public static Item GRAVEBIND_FOCUS;
	public static Item BLOODFURY_FOCUS;
	public static Item VOIDSTEP_FOCUS;
	public static Item HARVEST_FOCUS;
	public static Item FORAGER_FOCUS;
	public static Item TREMOR_FOCUS;
	public static Item BEACON_FOCUS;
	public static Item WAYSTONE_FOCUS;

	// The Unseen - stealth-flavoured Foci that work through patience, misdirection and openings.
	public static Item SOFTSTEP_FOCUS;
	public static Item VEIL_FOCUS;
	public static Item NEEDLE_FOCUS;
	public static Item SMOKE_FOCUS;

	// The Seafarers - peaceful utility Foci for fishing, wayfinding, and water-side travel.
	public static Item LINECAST_FOCUS;
	public static Item NETMENDER_FOCUS;
	public static Item HARBORLIGHT_FOCUS;
	public static Item DRIFTGLASS_FOCUS;

	// The Offshore - dangerous water utility for salvage, storms, and things below the waves.
	public static Item HARPOON_FOCUS;

	// The Radiant - Holy Foci built around vows, revelation, and measured judgment.
	public static Item VOTIVE_FOCUS;
	public static Item BELLWETHER_FOCUS;
	public static Item OATHGUARD_FOCUS;
	public static Item SUNLANCE_FOCUS;

	// The Reliquary - Holy utility Foci for relics, thresholds, names, and quiet rites.
	public static Item CENSER_FOCUS;
	public static Item NAMESAKE_FOCUS;
	public static Item THRESHOLD_FOCUS;

	// The Verdant Choir - broad naturalist Foci for grounded travel and gathering.
	public static Item ROOTSTEP_FOCUS;
	public static Item BLOOM_FOCUS;
	public static Item MOSSHEART_FOCUS;

	// The Ashen Forge - craft-bound Foci with restrained Bastion/Fury utility.
	public static Item TEMPER_FOCUS;
	public static Item KILNWARD_FOCUS;
	public static Item RIVET_FOCUS;

	// Additional Unseen Foci - evasive and misdirection tools with low direct damage.
	public static Item MASK_FOCUS;
	public static Item WHISPER_FOCUS;
	public static Item BLACKOUT_FOCUS;

	// The Revenant - unfinished endings, remembered deaths, debts, and grave-cold reprisals.
	public static Item EPITAPH_FOCUS;
	public static Item ASHEN_DEBT_FOCUS;
	public static Item HOLLOWSTEP_FOCUS;
	public static Item LAST_RITES_FOCUS;
	public static Item BONECHILL_FOCUS;

	// Umbral Eclipse - shadow Foci that wake in low light and total darkness.
	public static Item GLOOMSTRIDE_FOCUS;
	public static Item DUSKWARD_FOCUS;
	public static Item SHADOWMELD_FOCUS;
	public static Item DREADFANG_FOCUS;
	public static Item ECLIPSE_FOCUS;

	// Wheel of Refusals Foci - first batch beyond the original four affinities (Tide, Forge, Verdant, Umbral).
	public static Item UNDERTOW_FOCUS;
	public static Item RIPTIDE_HEART_FOCUS;
	public static Item PEARLGUARD_FOCUS;
	public static Item SLAGBRAND_FOCUS;
	public static Item ANVILHEART_FOCUS;
	public static Item SPARKWELD_FOCUS;
	public static Item THORNWAKE_FOCUS;
	public static Item SEEDCALL_FOCUS;
	public static Item BRAMBLEGATE_FOCUS;
	public static Item NULLVEIL_FOCUS;
	public static Item CINDERTHIEF_FOCUS;
	public static Item SNAREMOON_FOCUS;

	// Affinity Foci, batch one - pure-modifier Foci across the Tide, Verdant, Forge, Fury and Bastion lanes.
	public static Item TIDEWARDEN_FOCUS;
	public static Item WELLSPRING_FOCUS;
	public static Item CURRENT_RUNNER_FOCUS;
	public static Item SALTBRAND_FOCUS;
	public static Item EBBSTRIDE_FOCUS;
	public static Item OVERGROWTH_FOCUS;
	public static Item DEEPROOT_FOCUS;
	public static Item BRIARCOAT_FOCUS;
	public static Item FERNSTRIDE_FOCUS;
	public static Item SAPFLOW_FOCUS;
	public static Item CINDERPLATE_FOCUS;
	public static Item BELLOWSFURY_FOCUS;
	public static Item BLOODRUSH_FOCUS;
	public static Item RAVAGER_FOCUS;
	public static Item GRANITEHIDE_FOCUS;
	public static Item HAMMERWARD_FOCUS;

	// Deep Lanterns - cave rescue, route marking, and quiet expedition support.
	public static Item CAVEWICK_FOCUS;
	public static Item GLOWLINE_FOCUS;
	public static Item RESCUEFLAME_FOCUS;
	public static Item DEPTHGLASS_FOCUS;

	/** A consumable that permanently raises attunement capacity. Stacks normally. */
	public static Item ATTUNEMENT_SHARD;
	/** A fragment reward that smooths shard progression; four craft into one shard. */
	public static Item ATTUNEMENT_SHARD_FRAGMENT;
	/** A lightweight in-game guide to Foci, affinities, Pacts and Apex. */
	public static Item ATTUNEMENT_JOURNAL;
	/** A portable, stack-bound holder for spare Foci. */
	public static Item SATCHEL_OF_FOCI;
	/** A second-tier reliquary with twice the storage (54 slots) of the satchel. */
	public static Item GRAND_SATCHEL_OF_FOCI;

	/** The Attunement Altar - the home block where shards are bound into capacity. */
	public static Block ATTUNEMENT_ALTAR;
	/** The Altar of Reweaving converts three Foci and a shard fragment into a new Focus. */
	public static Block ALTAR_OF_REWEAVING;

	private static void registerAllContent() {
		registerFocus("swift_focus", item -> SWIFT_FOCUS = item);
		registerFocus("vital_focus", item -> VITAL_FOCUS = item);
		registerFocus("iron_focus", item -> IRON_FOCUS = item);

		registerFocus("leap_focus", item -> LEAP_FOCUS = item);
		registerFocus("edge_focus", item -> EDGE_FOCUS = item);
		registerFocus("frenzy_focus", item -> FRENZY_FOCUS = item);
		registerFocus("cinder_focus", item -> CINDER_FOCUS = item);
		registerFocus("bulwark_focus", item -> BULWARK_FOCUS = item);
		registerFocus("drift_focus", item -> DRIFT_FOCUS = item);

		registerFocus("tide_focus", item -> TIDE_FOCUS = item);
		registerFocus("galespur_focus", item -> GALESPUR_FOCUS = item);
		registerFocus("rainstep_focus", item -> RAINSTEP_FOCUS = item);
		registerFocus("updraft_focus", item -> UPDRAFT_FOCUS = item);
		registerFocus("emberward_focus", item -> EMBERWARD_FOCUS = item);
		registerFocus("anchor_focus", item -> ANCHOR_FOCUS = item);
		registerFocus("aegis_focus", item -> AEGIS_FOCUS = item);
		registerFocus("nightgaze_focus", item -> NIGHTGAZE_FOCUS = item);
		registerFocus("hearth_focus", item -> HEARTH_FOCUS = item);
		registerFocus("lantern_focus", item -> LANTERN_FOCUS = item);
		registerFocus("delver_focus", item -> DELVER_FOCUS = item);
		registerFocus("lodestone_focus", item -> LODESTONE_FOCUS = item);

		registerFocus("thornward_focus", item -> THORNWARD_FOCUS = item);
		registerFocus("leech_focus", item -> LEECH_FOCUS = item);

		registerFocus("stormcall_focus", item -> STORMCALL_FOCUS = item);
		registerFocus("gravebind_focus", item -> GRAVEBIND_FOCUS = item);
		registerFocus("bloodfury_focus", item -> BLOODFURY_FOCUS = item);
		registerFocus("voidstep_focus", item -> VOIDSTEP_FOCUS = item);
		registerFocus("harvest_focus", item -> HARVEST_FOCUS = item);
		registerFocus("forager_focus", item -> FORAGER_FOCUS = item);
		registerFocus("tremor_focus", item -> TREMOR_FOCUS = item);
		registerFocus("beacon_focus", item -> BEACON_FOCUS = item);
		registerFocus("waystone_focus", item -> WAYSTONE_FOCUS = item);

		registerFocus("softstep_focus", item -> SOFTSTEP_FOCUS = item);
		registerFocus("veil_focus", item -> VEIL_FOCUS = item);
		registerFocus("needle_focus", item -> NEEDLE_FOCUS = item);
		registerFocus("smoke_focus", item -> SMOKE_FOCUS = item);

		registerFocus("linecast_focus", item -> LINECAST_FOCUS = item);
		registerFocus("netmender_focus", item -> NETMENDER_FOCUS = item);
		registerFocus("harborlight_focus", item -> HARBORLIGHT_FOCUS = item);
		registerFocus("driftglass_focus", item -> DRIFTGLASS_FOCUS = item);

		registerFocus("harpoon_focus", item -> HARPOON_FOCUS = item);

		registerFocus("votive_focus", item -> VOTIVE_FOCUS = item);
		registerFocus("bellwether_focus", item -> BELLWETHER_FOCUS = item);
		registerFocus("oathguard_focus", item -> OATHGUARD_FOCUS = item);
		registerFocus("sunlance_focus", item -> SUNLANCE_FOCUS = item);

		registerFocus("censer_focus", item -> CENSER_FOCUS = item);
		registerFocus("namesake_focus", item -> NAMESAKE_FOCUS = item);
		registerFocus("threshold_focus", item -> THRESHOLD_FOCUS = item);

		registerFocus("rootstep_focus", item -> ROOTSTEP_FOCUS = item);
		registerFocus("bloom_focus", item -> BLOOM_FOCUS = item);
		registerFocus("mossheart_focus", item -> MOSSHEART_FOCUS = item);

		registerFocus("temper_focus", item -> TEMPER_FOCUS = item);
		registerFocus("kilnward_focus", item -> KILNWARD_FOCUS = item);
		registerFocus("rivet_focus", item -> RIVET_FOCUS = item);

		registerFocus("mask_focus", item -> MASK_FOCUS = item);
		registerFocus("whisper_focus", item -> WHISPER_FOCUS = item);
		registerFocus("blackout_focus", item -> BLACKOUT_FOCUS = item);

		registerFocus("epitaph_focus", item -> EPITAPH_FOCUS = item);
		registerFocus("ashen_debt_focus", item -> ASHEN_DEBT_FOCUS = item);
		registerFocus("hollowstep_focus", item -> HOLLOWSTEP_FOCUS = item);
		registerFocus("last_rites_focus", item -> LAST_RITES_FOCUS = item);
		registerFocus("bonechill_focus", item -> BONECHILL_FOCUS = item);

		registerFocus("gloomstride_focus", item -> GLOOMSTRIDE_FOCUS = item);
		registerFocus("duskward_focus", item -> DUSKWARD_FOCUS = item);
		registerFocus("shadowmeld_focus", item -> SHADOWMELD_FOCUS = item);
		registerFocus("dreadfang_focus", item -> DREADFANG_FOCUS = item);
		registerFocus("eclipse_focus", item -> ECLIPSE_FOCUS = item);

		registerFocus("undertow_focus", item -> UNDERTOW_FOCUS = item);
		registerFocus("riptide_heart_focus", item -> RIPTIDE_HEART_FOCUS = item);
		registerFocus("pearlguard_focus", item -> PEARLGUARD_FOCUS = item);
		registerFocus("slagbrand_focus", item -> SLAGBRAND_FOCUS = item);
		registerFocus("anvilheart_focus", item -> ANVILHEART_FOCUS = item);
		registerFocus("sparkweld_focus", item -> SPARKWELD_FOCUS = item);
		registerFocus("thornwake_focus", item -> THORNWAKE_FOCUS = item);
		registerFocus("seedcall_focus", item -> SEEDCALL_FOCUS = item);
		registerFocus("bramblegate_focus", item -> BRAMBLEGATE_FOCUS = item);
		registerFocus("nullveil_focus", item -> NULLVEIL_FOCUS = item);
		registerFocus("cinderthief_focus", item -> CINDERTHIEF_FOCUS = item);
		registerFocus("snaremoon_focus", item -> SNAREMOON_FOCUS = item);

		registerFocus("tidewarden_focus", item -> TIDEWARDEN_FOCUS = item);
		registerFocus("wellspring_focus", item -> WELLSPRING_FOCUS = item);
		registerFocus("current_runner_focus", item -> CURRENT_RUNNER_FOCUS = item);
		registerFocus("saltbrand_focus", item -> SALTBRAND_FOCUS = item);
		registerFocus("ebbstride_focus", item -> EBBSTRIDE_FOCUS = item);
		registerFocus("overgrowth_focus", item -> OVERGROWTH_FOCUS = item);
		registerFocus("deeproot_focus", item -> DEEPROOT_FOCUS = item);
		registerFocus("briarcoat_focus", item -> BRIARCOAT_FOCUS = item);
		registerFocus("fernstride_focus", item -> FERNSTRIDE_FOCUS = item);
		registerFocus("sapflow_focus", item -> SAPFLOW_FOCUS = item);
		registerFocus("cinderplate_focus", item -> CINDERPLATE_FOCUS = item);
		registerFocus("bellowsfury_focus", item -> BELLOWSFURY_FOCUS = item);
		registerFocus("bloodrush_focus", item -> BLOODRUSH_FOCUS = item);
		registerFocus("ravager_focus", item -> RAVAGER_FOCUS = item);
		registerFocus("granitehide_focus", item -> GRANITEHIDE_FOCUS = item);
		registerFocus("hammerward_focus", item -> HAMMERWARD_FOCUS = item);

		registerFocus("cavewick_focus", item -> CAVEWICK_FOCUS = item);
		registerFocus("glowline_focus", item -> GLOWLINE_FOCUS = item);
		registerFocus("rescueflame_focus", item -> RESCUEFLAME_FOCUS = item);
		registerFocus("depthglass_focus", item -> DEPTHGLASS_FOCUS = item);

		registerCustomFocusPool();

		register("attunement_shard", AttunementShardItem::new, item -> ATTUNEMENT_SHARD = item);
		register("attunement_shard_fragment", AttunementShardFragmentItem::new,
			item -> ATTUNEMENT_SHARD_FRAGMENT = item);
		register("attunement_journal", AttunementJournalItem::new, item -> ATTUNEMENT_JOURNAL = item);
		register("satchel_of_foci", SatchelItem::new, item -> SATCHEL_OF_FOCI = item);
		register("grand_satchel_of_foci", SatchelItem::grand, item -> GRAND_SATCHEL_OF_FOCI = item);

		registerAltar();
		registerReweavingAltar();
	}

	private static void registerCustomFocusPool() {
		for (int n = 1; n <= 8; n++) {
			registerFocus("custom_focus_" + n, REGISTERED_CUSTOM_FOCI::add);
		}
	}

	private static void registerFocus(String name, Consumer<Item> setter) {
		register(name, properties -> new Item(properties.stacksTo(1)), item -> {
			setter.accept(item);
			REGISTERED_FOCI.add(item);
		});
	}

	public static boolean isFocus(Item item) {
		return item != null && REGISTERED_FOCI.contains(item);
	}

	public static boolean isFocus(ItemStack stack) {
		return !stack.isEmpty() && isFocus(stack.getItem());
	}

	/**
	 * Registers an item with a custom {@link Item} subclass. Unlike the Focus
	 * helper this does not call {@code stacksTo(1)}, so the item stacks normally.
	 */
	private static <T extends Item> void register(
			String name, Function<Item.Properties, T> factory, Consumer<T> setter) {
		ResourceKey<Item> key = ResourceKey.create(
			Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, name));
		ForgeRegistration.item(name, () -> {
			T item = factory.apply(new Item.Properties());
			setter.accept(item);
			return item;
		});
	}

	/** Registers the Attunement Altar block and its matching block item. */
	private static void registerAltar() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "attunement_altar");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		ForgeRegistration.block("attunement_altar", () -> {
			Block block = new AttunementAltarBlock(BlockBehaviour.Properties.of()

				.strength(3.5F, 6.0F)
				.sound(SoundType.DEEPSLATE)
				.lightLevel(state -> 7)
				.noOcclusion());
			ATTUNEMENT_ALTAR = block;
			return block;
		});

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		ForgeRegistration.item("attunement_altar", () ->
			new BlockItem(requireBlock(ATTUNEMENT_ALTAR, "attunement_altar"),
				new Item.Properties()));
	}

	/** Registers the Altar of Reweaving block and its matching block item. */
	private static void registerReweavingAltar() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "altar_of_reweaving");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		ForgeRegistration.block("altar_of_reweaving", () -> {
			Block block = new AltarOfReweavingBlock(BlockBehaviour.Properties.of()

				.strength(3.5F, 6.0F)
				.sound(SoundType.DEEPSLATE)
				.lightLevel(state -> 6)
				.noOcclusion());
			ALTAR_OF_REWEAVING = block;
			return block;
		});

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		ForgeRegistration.item("altar_of_reweaving", () ->
			new BlockItem(requireBlock(ALTAR_OF_REWEAVING, "altar_of_reweaving"),
				new Item.Properties()));
	}

	private static Block requireBlock(Block block, String name) {
		if (block == null) {
			throw new IllegalStateException("Block item registered before block: " + name);
		}
		return block;
	}

	/**
	 * Schedules content registration, registers Focus behaviours, and registers the
	 * creative tabs.
	 */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		registerAllContent();
		AttunedFocusBehaviors.init();
		AttunedCreativeTabs.init();
	}
}
