package dev.attuned.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.attuned.menu.QuickApplyPresetPayload;
import dev.attuned.network.AbilityPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
	private static KeyMapping[] applyBuildKeys;
	private static boolean initialized;

	/** Registers the keybind and the tick watcher that sends the ability packet. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		abilityKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.attuned.ability", GLFW.GLFW_KEY_R, KeyMapping.Category.GAMEPLAY));
		toggleOwnAffinityHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.attuned.toggle_own_affinity_hud",
			InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		toggleEnemyAffinityHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.attuned.toggle_enemy_affinity_hud",
			InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		toggleFociHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.attuned.toggle_foci_hud",
			InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		applyBuildKeys = new KeyMapping[3];
		for (int i = 0; i < applyBuildKeys.length; i++) {
			applyBuildKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.attuned.apply_build_" + (i + 1),
				InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), KeyMapping.Category.GAMEPLAY));
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (abilityKey.consumeClick()) {
				// Buffered clicks can drain after a disconnect; only send with a live player.
				if (client.player != null) {
					ClientPlayNetworking.send(new AbilityPayload());
				}
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
			for (int i = 0; i < applyBuildKeys.length; i++) {
				while (applyBuildKeys[i].consumeClick()) {
					// Server validates index range, apply cooldown, and sourcing.
					if (client.player != null) {
						ClientPlayNetworking.send(new QuickApplyPresetPayload(i));
					}
				}
			}
		});
	}
}
