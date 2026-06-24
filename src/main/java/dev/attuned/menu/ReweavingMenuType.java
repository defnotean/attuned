package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.platform.ForgeRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

/** Registers and opens the Altar of Reweaving menu. */
public final class ReweavingMenuType {
	private static boolean initialized;

	private ReweavingMenuType() {}

	public static MenuType<ReweavingMenu> TYPE;
	public static final Component DISPLAY_NAME =
		Component.translatable("container.attuned.reweaving_altar");

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "altar_of_reweaving");
		TYPE = ForgeRegistration.menu(id.getPath(), new MenuType<>(ReweavingMenu::new, FeatureFlags.VANILLA_SET));
	}

	public static MenuProvider provider(Level level, BlockPos pos) {
		ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
		return new SimpleMenuProvider(
			(containerId, inventory, player) ->
				new ReweavingMenu(containerId, inventory, new SimpleContainer(ReweavingMenu.CONTAINER_SIZE), access),
			DISPLAY_NAME);
	}
}
