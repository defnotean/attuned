package dev.attuned.client;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.network.AttunementStatePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Applies the explicit NeoForge S2C mirror for Attuned's player attachments. */
@Environment(EnvType.CLIENT)
public final class AttunementStateClient {
	private static boolean initialized;

	private AttunementStateClient() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(AttunementStatePayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				var local = context.client().player;
				if (local != null) {
					AttunedAttachments.applySyncedState(local, payload);
					AttunementReadout.invalidate(local);
				}
			}));
	}
}
