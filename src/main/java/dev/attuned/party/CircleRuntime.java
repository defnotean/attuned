package dev.attuned.party;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.network.CircleSnapshotSync;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runtime owner for transient Circle state and cleanup hook registration. */
public final class CircleRuntime {
	private static final int DEFAULT_MAX_MEMBERS = 4;
	private static final long DEFAULT_INVITE_TTL_TICKS = 20L * 60L;
	private static final long DEFAULT_INVITE_COOLDOWN_TICKS = 20L * 10L;
	private static final long DEFAULT_CREATE_COOLDOWN_TICKS = 20L * 30L;
	private static CircleManager manager = newManager();
	private static boolean initialized;

	private CircleRuntime() {}

	public static void init() {
		if (initialized) {
			return;
		}
		manager = newManager();
		CircleContributions.init();
		CircleNavigationTargets.init();
		initialized = true;
		AttunedPlayerCleanup.onForgetPlayer(CircleRuntime::forgetPlayer);
		AttunedServerCleanup.onStop(() -> manager.clear());
	}

	public static CircleManager manager() {
		return manager;
	}

	public static int maxMembers() {
		return AttunedConfig.get().partyMaxMembers();
	}

	public static long inviteTtlSeconds() {
		return AttunedConfig.get().partyInviteTtlTicks() / 20L;
	}

	private static void forgetPlayer(ServerPlayer player) {
		List<UUID> members = manager.circleOf(player.getUUID())
			.map(CirclePolicy.Circle::members)
			.orElse(List.of());
		manager.forgetPlayer(player.getUUID());
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		for (UUID member : members) {
			if (member.equals(player.getUUID())) {
				continue;
			}
			ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
			if (memberPlayer != null) {
				CircleSnapshotSync.sync(memberPlayer);
			}
		}
	}

	private static CircleManager newManager() {
		return new CircleManager(AttunedConfig.get().partyMaxMembers(),
			AttunedConfig.get().partyInviteTtlTicks(),
			AttunedConfig.get().partyInviteCooldownTicks(),
			AttunedConfig.get().partyCreateCooldownTicks());
	}
}
