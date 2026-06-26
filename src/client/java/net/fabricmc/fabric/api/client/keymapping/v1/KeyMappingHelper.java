package net.fabricmc.fabric.api.client.keymapping.v1;

import dev.attuned.platform.NeoForgeEventBuses;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class KeyMappingHelper {
	private KeyMappingHelper() {}

	public static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
		NeoForgeEventBuses.modEventBus()
			.addListener((RegisterKeyMappingsEvent event) -> event.register(keyMapping));
		return keyMapping;
	}
}
