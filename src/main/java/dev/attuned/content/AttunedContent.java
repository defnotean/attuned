package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.content.behavior.AegisBehavior;
import dev.attuned.content.behavior.DelverBehavior;
import dev.attuned.content.behavior.EmberwardBehavior;
import dev.attuned.content.behavior.LodestoneBehavior;
import dev.attuned.content.behavior.NightgazeBehavior;
import dev.attuned.content.behavior.TideBehavior;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
	public static final Item EMBERWARD_FOCUS = register("emberward_focus");
	public static final Item AEGIS_FOCUS = register("aegis_focus");
	public static final Item NIGHTGAZE_FOCUS = register("nightgaze_focus");
	public static final Item DELVER_FOCUS = register("delver_focus");
	public static final Item LODESTONE_FOCUS = register("lodestone_focus");

	// Combat Foci — a separate combat system handles their effects.
	public static final Item THORNWARD_FOCUS = register("thornward_focus");
	public static final Item LEECH_FOCUS = register("leech_focus");

	/** A consumable that permanently raises attunement capacity. Stacks normally. */
	public static final Item ATTUNEMENT_SHARD = register("attunement_shard", AttunementShardItem::new);

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

	/**
	 * Forces this class to load so the items register, registers Focus
	 * behaviours, and registers the creative tab.
	 */
	public static void init() {
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "tide"), new TideBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "emberward"), new EmberwardBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "aegis"), new AegisBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "nightgaze"), new NightgazeBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "delver"), new DelverBehavior());
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "lodestone"), new LodestoneBehavior());
		registerCreativeTab();
	}

	/**
	 * Registers the Attuned creative-inventory tab — every Focus and the
	 * Attunement Shard, so the mod's items are reachable without {@code /give}.
	 */
	private static void registerCreativeTab() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.attuned"))
			.icon(() -> new ItemStack(ATTUNEMENT_SHARD))
			.displayItems((parameters, output) -> {
				output.accept(SWIFT_FOCUS);
				output.accept(VITAL_FOCUS);
				output.accept(IRON_FOCUS);
				output.accept(LEAP_FOCUS);
				output.accept(EDGE_FOCUS);
				output.accept(FRENZY_FOCUS);
				output.accept(BULWARK_FOCUS);
				output.accept(DRIFT_FOCUS);
				output.accept(TIDE_FOCUS);
				output.accept(EMBERWARD_FOCUS);
				output.accept(AEGIS_FOCUS);
				output.accept(NIGHTGAZE_FOCUS);
				output.accept(DELVER_FOCUS);
				output.accept(LODESTONE_FOCUS);
				output.accept(THORNWARD_FOCUS);
				output.accept(LEECH_FOCUS);
				output.accept(ATTUNEMENT_SHARD);
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "attuned"), tab);
	}
}
