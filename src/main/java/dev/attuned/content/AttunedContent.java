package dev.attuned.content;

import dev.attuned.Attuned;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * The bundled Focus items shipped with Attuned. Each item's accessory behaviour
 * (cost, modifiers) lives in a datapack {@code FocusDefinition}, not here.
 */
public final class AttunedContent {
	private AttunedContent() {}

	public static final Item SWIFT_FOCUS = register("swift_focus");
	public static final Item VITAL_FOCUS = register("vital_focus");
	public static final Item IRON_FOCUS = register("iron_focus");

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

	/** Forces this class to load so the items register during mod init. */
	public static void init() {}
}
