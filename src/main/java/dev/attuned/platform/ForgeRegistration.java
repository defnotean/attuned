package dev.attuned.platform;

import dev.attuned.Attuned;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Small Forge registration bridge for code shared with the Fabric source layout. */
public final class ForgeRegistration {
	private static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, Attuned.MOD_ID);
	private static final DeferredRegister<Block> BLOCKS =
		DeferredRegister.create(ForgeRegistries.BLOCKS, Attuned.MOD_ID);
	private static final DeferredRegister<MenuType<?>> MENU_TYPES =
		DeferredRegister.create(ForgeRegistries.MENU_TYPES, Attuned.MOD_ID);
	private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
		DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Attuned.MOD_ID);
	private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Attuned.MOD_ID);

	private static boolean registered;

	private ForgeRegistration() {}

	public static void registerAll(IEventBus modEventBus) {
		if (registered) {
			return;
		}
		registered = true;
		ITEMS.register(modEventBus);
		BLOCKS.register(modEventBus);
		MENU_TYPES.register(modEventBus);
		DATA_COMPONENT_TYPES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
	}

	public static <T extends Item> void item(String name, Supplier<? extends T> item) {
		ITEMS.register(name, item);
	}

	public static <T extends Block> void block(String name, Supplier<? extends T> block) {
		BLOCKS.register(name, block);
	}

	public static <T extends MenuType<?>> T menu(String name, T menuType) {
		MENU_TYPES.register(name, () -> menuType);
		return menuType;
	}

	public static <T> DataComponentType<T> dataComponent(String name, DataComponentType<T> componentType) {
		DATA_COMPONENT_TYPES.register(name, () -> componentType);
		return componentType;
	}

	public static void creativeModeTab(String name, Supplier<CreativeModeTab> tab) {
		CREATIVE_MODE_TABS.register(name, tab);
	}
}
