package dev.attuned.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Common registration for Attuned server-to-client payloads. */
public final class JournalNetworking {
	private JournalNetworking() {}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenJournalPayload.TYPE, OpenJournalPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TremorOreHintPayload.TYPE, TremorOreHintPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FocusAbilityStatusPayload.TYPE, FocusAbilityStatusPayload.CODEC);
	}
}
