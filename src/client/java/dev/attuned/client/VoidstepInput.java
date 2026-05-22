package dev.attuned.client;

import dev.attuned.network.VoidstepPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Detects the Voidstep gesture client-side — jump double-tapped while sneaking —
 * and asks the server to perform the teleport. The server owns all validation;
 * this only recognises the gesture and sends the request.
 */
public final class VoidstepInput {
	private VoidstepInput() {}

	/** Maximum gap between the two jump presses, in client ticks. */
	private static final int DOUBLE_TAP_WINDOW = 7;

	private static boolean jumpWasDown;
	private static int ticksSinceFirstTap = Integer.MAX_VALUE;

	/** Registers the client-tick gesture watcher. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(VoidstepInput::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.screen != null) {
			jumpWasDown = false;
			ticksSinceFirstTap = Integer.MAX_VALUE;
			return;
		}
		if (ticksSinceFirstTap != Integer.MAX_VALUE) {
			ticksSinceFirstTap++;
		}
		boolean jumpDown = client.options.keyJump.isDown();
		boolean freshPress = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		if (!freshPress) {
			return;
		}
		if (ticksSinceFirstTap <= DOUBLE_TAP_WINDOW && client.player.isShiftKeyDown()) {
			// Second tap, in time, while sneaking — fire and disarm.
			ticksSinceFirstTap = Integer.MAX_VALUE;
			ClientPlayNetworking.send(new VoidstepPayload());
		} else {
			// First tap (or a stale one) — start the window.
			ticksSinceFirstTap = 0;
		}
	}
}
