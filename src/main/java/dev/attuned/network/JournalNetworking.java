package dev.attuned.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Common registration for the custom Attunement Journal open-screen payload.
 */
public final class JournalNetworking {
	private JournalNetworking() {}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenJournalPayload.TYPE, OpenJournalPayload.CODEC);
	}
}
