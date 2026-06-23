package dev.attuned.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Common registration for Attuned server-to-client payloads. */
public final class JournalNetworking {
	private static boolean initialized;

	private JournalNetworking() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		PayloadTypeRegistry.clientboundPlay().register(OpenJournalPayload.TYPE, OpenJournalPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TremorOreHintPayload.TYPE, TremorOreHintPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FocusAbilityStatusPayload.TYPE, FocusAbilityStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CircleSnapshotPayload.TYPE, CircleSnapshotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CircleInvitePromptPayload.TYPE, CircleInvitePromptPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CirclePingClientPayload.TYPE, CirclePingClientPayload.CODEC);
		CircleSnapshotSync.init();
	}
}
