package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.platform.ForgeRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

/**
 * Holds the registered {@link MenuType} for the {@link AltarMenu} and the
 * {@link MenuProvider} factory the Altar block uses when a player opens it.
 *
 * <p>Registering a menu type follows the vanilla pattern: a {@code MenuType<T>}
 * is built around a constructor reference and registered into the {@code MENU}
 * registry. Fabric's menu-api access-widens the {@code MenuType} constructor,
 * which is what lets external code instantiate one.
 */
public final class AltarMenuType {
	private static boolean initialized;

	private AltarMenuType() {}

	/** The Altar menu's registered type — populated by {@link #init()}. */
	public static MenuType<AltarMenu> TYPE;

	/** Window title shown above the slot. */
	public static final Component DISPLAY_NAME =
		new net.minecraft.network.chat.TranslatableComponent("container.attuned.altar");

	/** Forces this class to load and registers the menu type. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ResourceLocation id = new ResourceLocation(Attuned.MOD_ID, "attunement_altar");
		TYPE = ForgeRegistration.menu(id.getPath(), new MenuType<>(AltarMenu::new));
	}

	/**
	 * A {@link MenuProvider} the Altar block uses to open the menu for a player.
	 * The Altar holds no per-block state, so a {@link SimpleMenuProvider} that
	 * builds a fresh menu on demand is enough; we close over the block's level
	 * and position via a {@link ContainerLevelAccess} so the bind handler — and
	 * the menu's own reachability check — resolve against the right dimension.
	 */
	public static MenuProvider provider(Level level, BlockPos pos) {
		ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
		return new SimpleMenuProvider(
			(containerId, inventory, player) ->
				new AltarMenu(containerId, inventory, new SimpleContainer(AltarMenu.INPUT_SIZE), access),
			DISPLAY_NAME);
	}
}
