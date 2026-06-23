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
		PayloadTypeRegistry.playS2C().register(OpenJournalPayload.TYPE, OpenJournalPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TremorOreHintPayload.TYPE, TremorOreHintPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FocusAbilityStatusPayload.TYPE, FocusAbilityStatusPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CircleSnapshotPayload.TYPE, CircleSnapshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CircleInvitePromptPayload.TYPE, CircleInvitePromptPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CirclePingClientPayload.TYPE, CirclePingClientPayload.CODEC);
		CircleSnapshotSync.init();
	}
}
