package dev.attuned.client.screen;

import dev.attuned.menu.AltarMenuType;
import dev.attuned.menu.ReweavingMenuType;
import dev.attuned.menu.SatchelMenuType;
import dev.attuned.platform.NeoForgeEventBuses;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side hook-up for the Altar's screen: maps the registered Altar menu
 * type to the {@link AltarScreen} so the vanilla open-screen packet builds the
 * right screen client-side. Kept separate from {@link AltarScreen} itself so
 * {@code AttunedClient} only has to call a single {@code init()}.
 */
public final class AltarScreens {
	private AltarScreens() {}

	private static boolean initialized;

	/** Registers the Altar's screen factory against its menu type. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		NeoForgeEventBuses.modEventBus().addListener(AltarScreens::register);
	}

	private static void register(RegisterMenuScreensEvent event) {
		event.register(AltarMenuType.TYPE, AltarScreen::new);
		event.register(ReweavingMenuType.TYPE, ReweavingScreen::new);
		event.register(SatchelMenuType.TYPE, SatchelScreen::new);
		event.register(SatchelMenuType.GRAND_TYPE, SatchelScreen::new);
	}
}
