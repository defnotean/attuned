package dev.attuned.client;

import net.fabricmc.api.ClientModInitializer;

public class AttunedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AttunedTooltips.init();
		AttunedKeybinds.init();
	}
}
