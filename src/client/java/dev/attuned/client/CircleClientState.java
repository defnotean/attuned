package dev.attuned.client;

import dev.attuned.network.CircleInvitePromptPayload;
import dev.attuned.network.CirclePingClientPayload;
import dev.attuned.network.CircleSnapshotPayload;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client mirror of the latest server-owned public Circle snapshot. */
@Environment(EnvType.CLIENT)
public final class CircleClientState {
	private static CircleSnapshotPayload snapshot = CircleSnapshotPayload.EMPTY;
	private static CircleInvitePromptPayload invitePrompt;
	private static CirclePingClientPayload ping;
	private static boolean initialized;

	private CircleClientState() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ClientPlayNetworking.registerGlobalReceiver(CircleSnapshotPayload.TYPE, (payload, context) ->
			context.client().execute(() -> accept(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CircleInvitePromptPayload.TYPE, (payload, context) ->
			context.client().execute(() -> acceptPrompt(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CirclePingClientPayload.TYPE, (payload, context) ->
			context.client().execute(() -> acceptPing(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(CircleClientState::tick);
	}

	static void accept(CircleSnapshotPayload payload) {
		snapshot = payload == null ? CircleSnapshotPayload.EMPTY : payload;
	}

	static void acceptPrompt(CircleInvitePromptPayload payload) {
		invitePrompt = payload;
	}

	static void acceptPing(CirclePingClientPayload payload) {
		ping = payload;
	}

	public static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			clear();
		}
	}

	private static void clear() {
		snapshot = CircleSnapshotPayload.EMPTY;
		invitePrompt = null;
		ping = null;
	}

	public static String name() {
		return snapshot.name();
	}

	public static List<CircleSnapshotPayload.Member> members() {
		return snapshot.members();
	}

	public static java.util.Optional<CircleInvitePromptPayload> invitePrompt() {
		return Optional.ofNullable(invitePrompt);
	}

	public static java.util.Optional<CirclePingClientPayload> ping() {
		return Optional.ofNullable(ping);
	}
}
