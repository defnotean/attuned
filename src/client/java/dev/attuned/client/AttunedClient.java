package dev.attuned.client;

import dev.attuned.client.hud.CombatHud;
import dev.attuned.client.screen.AltarScreens;
import net.fabricmc.api.ClientModInitializer;

public class AttunedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AttunedTooltips.init();
		AttunedKeybinds.init();
		CombatHud.init();
		AltarScreens.init();
	}
}
