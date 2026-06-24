package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public final class KeyMappingHelper {
	private KeyMappingHelper() {}

	public static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
		RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(keyMapping));
		return keyMapping;
	}
}
