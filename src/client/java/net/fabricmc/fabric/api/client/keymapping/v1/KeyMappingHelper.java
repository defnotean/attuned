package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;

public final class KeyMappingHelper {
	private KeyMappingHelper() {}

	public static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
		return keyMapping;
	}
}
