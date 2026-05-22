package dev.attuned.client;

import dev.attuned.network.AbilityPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The Focus-ability keybind. Pressing it asks the server to trigger the active
 * ability of every active Focus the player carries; the server owns all
 * validation.
 */
public final class AttunedKeybinds {
	private AttunedKeybinds() {}

	private static KeyMapping abilityKey;

	/** Registers the keybind and the tick watcher that sends the ability packet. */
	public static void init() {
		abilityKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.attuned.ability", GLFW.GLFW_KEY_R, KeyMapping.Category.GAMEPLAY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (abilityKey.consumeClick()) {
				ClientPlayNetworking.send(new AbilityPayload());
			}
		});
	}
}
