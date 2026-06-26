package dev.attuned.client;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.network.AttunedStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client receiver for the 1.19.4 branch-local player state sync packet. */
public final class AttunedStateClientSync {
	private static boolean initialized;

	private AttunedStateClientSync() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(AttunedStatePayload.TYPE, (client, handler, buf, sender) -> {
			AttunedStatePayload payload = AttunedStatePayload.TYPE.read(buf);
			client.execute(() -> {
				LocalPlayer local = client.player;
				if (local != null) {
					AttunedAttachments.applySync(local, payload.tag());
					AttunementReadout.invalidate(local);
				}
			});
		});
	}
}
