package dev.attuned.client.screen;

import dev.attuned.menu.AltarMenuType;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client-side hook-up for the Altar's screen: maps the registered Altar menu
 * type to the {@link AltarScreen} so the vanilla open-screen packet builds the
 * right screen client-side. Kept separate from {@link AltarScreen} itself so
 * {@code AttunedClient} only has to call a single {@code init()}.
 */
public final class AltarScreens {
	private AltarScreens() {}

	/** Registers the Altar's screen factory against its menu type. */
	public static void init() {
		MenuScreens.register(AltarMenuType.TYPE, AltarScreen::new);
	}
}
