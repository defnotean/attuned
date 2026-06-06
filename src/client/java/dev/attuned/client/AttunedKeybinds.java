package dev.attuned.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.attuned.network.AbilityPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The Focus Ability keybind. Pressing it asks the server to trigger the active
 * ability of the player's one active ability Focus; the server owns all validation.
 */
public final class AttunedKeybinds {
	private AttunedKeybinds() {}

	private static KeyMapping abilityKey;
	private static KeyMapping toggleOwnAffinityHudKey;
	private static KeyMapping toggleEnemyAffinityHudKey;
	private static KeyMapping toggleFociHudKey;
	private static boolean initialized;

	/** Registers the keybind and the tick watcher that sends the ability packet. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		abilityKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.attuned.ability", GLFW.GLFW_KEY_R, KeyMapping.Category.GAMEPLAY));
		toggleOwnAffinityHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.attuned.toggle_own_affinity_hud",
			InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		toggleEnemyAffinityHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.attuned.toggle_enemy_affinity_hud",
			InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		toggleFociHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.attuned.toggle_foci_hud",
			InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (abilityKey.consumeClick()) {
				ClientPlayNetworking.send(new AbilityPayload());
			}
			while (toggleOwnAffinityHudKey.consumeClick()) {
				AttunedClientConfig.toggleOwnAffinityHud();
			}
			while (toggleEnemyAffinityHudKey.consumeClick()) {
				AttunedClientConfig.toggleEnemyAffinityHud();
			}
			while (toggleFociHudKey.consumeClick()) {
				AttunedClientConfig.toggleFociHud();
			}
		});
	}
}
