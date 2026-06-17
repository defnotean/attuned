package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.content.AttunedContent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/** Registers the Focus Reliquary menu types (small + grand) and hand-bound providers. */
public final class SatchelMenuType {
	private static boolean initialized;

	private SatchelMenuType() {}

	public static MenuType<SatchelMenu> TYPE;
	/** Second-tier (54-slot) Grand Focus Reliquary menu type; shares SatchelScreen. */
	public static MenuType<SatchelMenu> GRAND_TYPE;

	public static final Component DISPLAY_NAME =
		Component.translatable("container.attuned.satchel");
	public static final Component GRAND_DISPLAY_NAME =
		Component.translatable("container.attuned.grand_satchel");

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		TYPE = Registry.register(BuiltInRegistries.MENU,
			new ResourceLocation(Attuned.MOD_ID, "satchel_of_foci"),
			new MenuType<>(SatchelMenu::new));
		GRAND_TYPE = Registry.register(BuiltInRegistries.MENU,
			new ResourceLocation(Attuned.MOD_ID, "grand_satchel_of_foci"),
			new MenuType<>(SatchelMenu::grand));
	}

	public static MenuProvider provider(Player player, InteractionHand hand) {
		return provider(player, hand, false);
	}

	public static MenuProvider provider(Player player, InteractionHand hand, boolean grand) {
		if (grand) {
			return new SimpleMenuProvider(
				(containerId, inventory, p) -> new SatchelMenu(containerId, inventory,
					new SatchelContainer(p, hand, AttunedContent.GRAND_SATCHEL_OF_FOCI,
						dev.attuned.content.AttunedComponents.GRAND_SATCHEL_SIZE, true),
					hand, GRAND_TYPE),
				GRAND_DISPLAY_NAME);
		}
		return new SimpleMenuProvider(
			(containerId, inventory, p) -> new SatchelMenu(containerId, inventory,
				new SatchelContainer(p, hand), hand, TYPE),
			DISPLAY_NAME);
	}
}
