package dev.attuned.network;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.Attunement;
import dev.attuned.party.CirclePolicy;
import dev.attuned.party.CircleRuntime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Builds and sends public Circle roster snapshots from server-owned state. */
public final class CircleSnapshotSync {
	private static final long REFRESH_INTERVAL_TICKS = 20L;
	private static final Map<UUID, CircleSnapshotPayload> LAST_SENT = new HashMap<>();
	private static boolean initialized;

	private CircleSnapshotSync() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(CircleSnapshotSync::tick);
		AttunedPlayerCleanup.onForget(LAST_SENT::remove);
		AttunedServerCleanup.onStop(LAST_SENT::clear);
	}

	public static void syncAfter(MinecraftServer server, CirclePolicy.Result result, ServerPlayer... affected) {
		Set<ServerPlayer> recipients = new LinkedHashSet<>();
		if (result.circle().isPresent()) {
			for (UUID member : result.circle().orElseThrow().members()) {
				ServerPlayer player = server.getPlayerList().getPlayer(member);
				if (player != null) {
					recipients.add(player);
				}
			}
		}
		if (affected != null) {
			for (ServerPlayer player : affected) {
				if (player != null) {
					recipients.add(player);
				}
			}
		}
		for (ServerPlayer player : recipients) {
			sync(player);
		}
	}

	public static void sync(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!AttunedConfig.get().partyHudEnabled()) {
			sendIfChanged(player, CircleSnapshotPayload.EMPTY);
			return;
		}
		Optional<CirclePolicy.Circle> current = CircleRuntime.manager().circleOf(player.getUUID());
		if (server == null || current.isEmpty()) {
			sendIfChanged(player, CircleSnapshotPayload.EMPTY);
			return;
		}
		CirclePolicy.Circle circle = current.orElseThrow();
		sendIfChanged(player, snapshotFor(server, circle));
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % REFRESH_INTERVAL_TICKS != 0L) {
			return;
		}
		// Drop invites whose TTL elapsed without an accept so they do not linger
		// in circle state until the invitee accepts or disconnects.
		CircleRuntime.manager().pruneExpiredInvites(now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (CircleRuntime.manager().circleOf(player.getUUID()).isPresent()) {
				sync(player);
			}
		}
	}

	private static CircleSnapshotPayload snapshotFor(MinecraftServer server, CirclePolicy.Circle circle) {
		List<CircleSnapshotPayload.Member> members = circle.members().stream()
			.map(server.getPlayerList()::getPlayer)
			.filter(player -> player != null)
			.map(player -> {
				PublicAttunementSummary summary = publicSummary(player);
				return new CircleSnapshotPayload.Member(player.getUUID(),
					player.getName().getString(),
					player.getUUID().equals(circle.leader()),
					summary.stance(),
					summary.role(),
					summary.activeCount(),
					summary.dormantCount());
			})
			.toList();
		return new CircleSnapshotPayload(circle.name(), members);
	}

	private static PublicAttunementSummary publicSummary(ServerPlayer member) {
		var resolution = Attunement.resolution(member);
		int activeCount = resolution.activeSlots().size();
		int dormantCount = resolution.dormantReasons().size();
		return new PublicAttunementSummary(publicStance(member, activeCount),
			Attunement.publicRole(member), activeCount, dormantCount);
	}

	private static String publicStance(ServerPlayer member, int activeCount) {
		if (activeCount <= 0) {
			return "None";
		}
		if (Attunement.isDiscord(member)) {
			return "Discord";
		}
		return Attunement.committedAffinity(member)
			.map(CircleSnapshotSync::affinityName)
			.orElse("Neutral");
	}

	private static String affinityName(Affinity affinity) {
		String lower = affinity.getSerializedName().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static void sendIfChanged(ServerPlayer player, CircleSnapshotPayload payload) {
		if (payload.equals(LAST_SENT.get(player.getUUID()))) {
			return;
		}
		LAST_SENT.put(player.getUUID(), payload);
		ServerPlayNetworking.send(player, payload);
	}

	private record PublicAttunementSummary(String stance, String role, int activeCount, int dormantCount) {}
}
