package net.fabricmc.fabric.api.client.keybinding.v1;

import com.mojang.blaze3d.platform.InputConstants;
import dev.attuned.platform.NeoForgeEventBuses;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class KeyBindingHelper {
	private KeyBindingHelper() {}

	public static KeyMapping registerKeyBinding(KeyMapping keyMapping) {
		NeoForgeEventBuses.modEventBus()
			.addListener((RegisterKeyMappingsEvent event) -> event.register(keyMapping));
		return keyMapping;
	}

	public static InputConstants.Key getBoundKeyOf(KeyMapping keyMapping) {
		return keyMapping.getKey();
	}
}
