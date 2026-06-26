package dev.attuned.platform;

import dev.attuned.Attuned;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoForgeDeferredRegistries {
	private static final DeferredRegister.DataComponents DATA_COMPONENTS =
		DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Attuned.MOD_ID);
	private static final DeferredRegister.Items ITEMS =
		DeferredRegister.createItems(Attuned.MOD_ID);
	private static final DeferredRegister.Blocks BLOCKS =
		DeferredRegister.createBlocks(Attuned.MOD_ID);
	private static final DeferredRegister<MenuType<?>> MENUS =
		DeferredRegister.create(Registries.MENU, Attuned.MOD_ID);
	private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Attuned.MOD_ID);

	private static boolean registered;

	private NeoForgeDeferredRegistries() {}

	public static synchronized void register(IEventBus eventBus) {
		if (registered) {
			return;
		}
		registered = true;
		DATA_COMPONENTS.register(eventBus);
		BLOCKS.register(eventBus);
		ITEMS.register(eventBus);
		MENUS.register(eventBus);
		CREATIVE_MODE_TABS.register(eventBus);
	}

	public static <T> DataComponentType<T> dataComponent(Identifier id, DataComponentType<T> type) {
		DATA_COMPONENTS.register(path(id), () -> type);
		return type;
	}

	public static <T extends Item> DeferredItem<T> item(String name, Supplier<? extends T> factory) {
		return ITEMS.register(name, factory);
	}

	public static <T extends Item> DeferredItem<T> item(String name, Function<Item.Properties, ? extends T> factory) {
		return ITEMS.registerItem(name, factory);
	}

	public static <T extends Block> DeferredBlock<T> block(String name, Supplier<? extends T> factory) {
		return BLOCKS.register(name, factory);
	}

	public static <T extends Block> DeferredBlock<T> block(String name,
			Function<BlockBehaviour.Properties, ? extends T> factory) {
		return BLOCKS.registerBlock(name, factory);
	}

	public static <T extends MenuType<?>> T menu(Identifier id, T type) {
		MENUS.register(path(id), () -> type);
		return type;
	}

	public static CreativeModeTab creativeTab(Identifier id, CreativeModeTab tab) {
		CREATIVE_MODE_TABS.register(path(id), () -> tab);
		return tab;
	}

	private static String path(Identifier id) {
		Objects.requireNonNull(id, "id");
		if (!Attuned.MOD_ID.equals(id.getNamespace())) {
			throw new IllegalArgumentException("Expected " + Attuned.MOD_ID + " registry id, got " + id);
		}
		return id.getPath();
	}
}
