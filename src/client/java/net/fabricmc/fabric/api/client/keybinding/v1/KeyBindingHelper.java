package net.fabricmc.fabric.api.client.keybinding.v1;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class KeyBindingHelper {
	private KeyBindingHelper() {}

	public static KeyMapping registerKeyBinding(KeyMapping keyMapping) {
		FMLJavaModLoadingContext.get().getModEventBus()
			.addListener((RegisterKeyMappingsEvent event) -> event.register(keyMapping));
		return keyMapping;
	}

	public static InputConstants.Key getBoundKeyOf(KeyMapping keyMapping) {
		return keyMapping.getKey();
	}
}
