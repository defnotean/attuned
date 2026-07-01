package dev.attuned.quilt;

import dev.attuned.client.AttunedClient;
import net.fabricmc.api.ClientModInitializer;

public final class AttunedQuiltClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		new AttunedClient().onInitializeClient();
	}
}
