package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Registers the Satchel of Foci menu type and hand-bound provider. */
public final class SatchelMenuType {
	private static boolean initialized;

	private SatchelMenuType() {}

	public static MenuType<SatchelMenu> TYPE;

	public static final Component DISPLAY_NAME =
		Component.translatable("container.attuned.satchel");

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "satchel_of_foci");
		TYPE = Registry.register(BuiltInRegistries.MENU, id,
			new MenuType<>(SatchelMenu::new, FeatureFlags.VANILLA_SET));
	}

	public static MenuProvider provider(Player player, InteractionHand hand) {
		return new SimpleMenuProvider(
			(containerId, inventory, p) -> new SatchelMenu(containerId, inventory, new SatchelContainer(p, hand), hand),
			DISPLAY_NAME);
	}
}
