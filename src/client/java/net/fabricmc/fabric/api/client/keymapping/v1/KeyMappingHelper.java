package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class KeyMappingHelper {
	private KeyMappingHelper() {}

	public static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
		FMLJavaModLoadingContext.get().getModEventBus()
			.addListener((RegisterKeyMappingsEvent event) -> event.register(keyMapping));
		return keyMapping;
	}
}
