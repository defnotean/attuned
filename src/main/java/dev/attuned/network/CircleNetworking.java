package dev.attuned.network;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.compat.NetworkPackets;
import dev.attuned.compat.PlayerMessages;
import dev.attuned.party.CircleContributions;
import dev.attuned.party.CircleNavigationTargets;
import dev.attuned.party.CirclePingPolicy;
import dev.attuned.party.CirclePolicy;
import dev.attuned.party.CircleRuntime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative request handlers for transient Attuned Circles. */
public final class CircleNetworking {
	private static final double PING_RANGE_BLOCKS = 64.0D;
	private static final long PING_COOLDOWN_TICKS = 20L;
	private static final long PING_VISIBLE_TICKS = 120L;
	private static final Map<UUID, Long> LAST_PING = new HashMap<>();
	private static boolean initialized;

	private CircleNetworking() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedServerCleanup.onStop(LAST_PING::clear);
		AttunedPlayerCleanup.onForget(LAST_PING::remove);

		ServerPlayNetworking.registerGlobalReceiver(CircleCreatePayload.TYPE, (server, player, handler, buf, sender) -> {
			CircleCreatePayload payload = new CircleCreatePayload(buf);
			server.execute(() -> {
				CirclePolicy.Result result =
					CircleRuntime.manager().create(player.getUUID(), payload.name(), nowTick(player));
				CircleContributions.forgetIfMembershipChanged(result, player);
				CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player);
				sendResult(player, result.status());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CircleInvitePayload.TYPE, (server, player, handler, buf, sender) -> {
			CircleInvitePayload payload = new CircleInvitePayload(buf);
			server.execute(() -> {
				ServerPlayer target = player.getLevel().getServer()
					.getPlayerList().getPlayer(payload.target());
				if (target == null) {
					PlayerMessages.system(player, new net.minecraft.network.chat.TranslatableComponent("circle.attuned.status.target_offline")
						.withStyle(ChatFormatting.RED));
					return;
				}
				CirclePolicy.Result result =
					CircleRuntime.manager().invite(player.getUUID(), payload.target(), nowTick(player));
				CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player, target);
				sendResult(player, result.status());
				if (result.status() == CirclePolicy.Status.OK) {
					sendInviteNotice(target, player, result.circle().orElseThrow());
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CircleAcceptPayload.TYPE, (server, player, handler, buf, sender) -> {
			CircleAcceptPayload payload = new CircleAcceptPayload(buf);
			server.execute(() -> {
				ServerPlayer inviter = player.getLevel().getServer()
					.getPlayerList().getPlayer(payload.inviter());
				CirclePolicy.Result result =
					CircleRuntime.manager().accept(player.getUUID(), payload.inviter(), nowTick(player));
				CircleContributions.forgetIfMembershipChanged(result, player);
				CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player, inviter);
				sendResult(player, result.status());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CircleLeavePayload.TYPE, (server, player, handler, buf, sender) -> {
			new CircleLeavePayload(buf);
			server.execute(() -> {
				CirclePolicy.Result result = CircleRuntime.manager().leave(player.getUUID(), nowTick(player));
				CircleContributions.forgetIfMembershipChanged(result, player);
				CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player);
				sendResult(player, result.status());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CircleDisbandPayload.TYPE, (server, player, handler, buf, sender) -> {
			new CircleDisbandPayload(buf);
			server.execute(() -> {
				Optional<CirclePolicy.Circle> previousCircle =
					CircleRuntime.manager().circleOf(player.getUUID());
				CirclePolicy.Result result =
					CircleRuntime.manager().disband(player.getUUID(), nowTick(player));
				ServerPlayer[] affected = onlineMembers(player, previousCircle);
				CircleContributions.forgetIfMembershipChanged(result, affected);
				CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, affected);
				sendResult(player, result.status());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CircleKickPayload.TYPE, (server, player, handler, buf, sender) -> {
			CircleKickPayload payload = new CircleKickPayload(buf);
			server.execute(() -> {
				ServerPlayer target = player.getLevel().getServer()
					.getPlayerList().getPlayer(payload.target());
				CirclePolicy.Result result = CircleRuntime.manager().kick(player.getUUID(), payload.target());
				CircleContributions.forgetIfMembershipChanged(result, target);
				CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player, target);
				sendResult(player, result.status());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CirclePingPayload.TYPE, (server, player, handler, buf, sender) -> {
			CirclePingPayload payload = new CirclePingPayload(buf);
			server.execute(() -> handlePing(player, payload));
		});
	}

	private static long nowTick(ServerPlayer player) {
		return player.getLevel().getGameTime();
	}

	private static ServerPlayer[] onlineMembers(ServerPlayer player,
			Optional<CirclePolicy.Circle> previousCircle) {
		if (previousCircle.isEmpty()) {
			return new ServerPlayer[] { player };
		}
		return previousCircle.orElseThrow().members().stream()
			.map(member -> player.getLevel().getServer().getPlayerList().getPlayer(member))
			.filter(member -> member != null)
			.toArray(ServerPlayer[]::new);
	}

	private static void handlePing(ServerPlayer player, CirclePingPayload payload) {
		Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
		if (maybeCircle.isEmpty()) {
			return;
		}
		CirclePolicy.Circle circle = maybeCircle.orElseThrow();
		BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
		boolean targetLoaded = player.getLevel().hasChunkAt(pos);
		boolean targetVisible = pingVisible(player, payload.x(), payload.y(), payload.z());
		long now = nowTick(player);
		long lastPing = LAST_PING.getOrDefault(player.getUUID(), now - PING_COOLDOWN_TICKS);
		double distanceSquared = distanceSquared(player, payload);
		CirclePingPolicy.PingContext ping = new CirclePingPolicy.PingContext(
			player.getUUID(), true, true, targetLoaded, targetVisible, distanceSquared, lastPing);
		if (!CirclePingPolicy.allowed(ping, now, PING_COOLDOWN_TICKS, PING_RANGE_BLOCKS)) {
			return;
		}
		MinecraftServer server = player.getLevel().getServer();
		if (server == null) {
			return;
		}
		LAST_PING.put(player.getUUID(), now);
		CircleNavigationTargets.rememberPing(circle, player.getLevel().dimension(), pos, now, now + PING_VISIBLE_TICKS);
		for (UUID memberId : circle.members()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			if (member != null && member.getLevel() == player.getLevel()) {
				NetworkPackets.send(member, new CirclePingClientPayload(
					player.getUUID(),
					player.getName().getString(),
					payload.x(),
					payload.y(),
					payload.z(),
					now + PING_VISIBLE_TICKS));
			}
		}
	}

	private static double distanceSquared(ServerPlayer player, CirclePingPayload payload) {
		double x = payload.x() - player.getX();
		double y = payload.y() - player.getY();
		double z = payload.z() - player.getZ();
		return x * x + y * y + z * z;
	}

	private static boolean pingVisible(ServerPlayer player, double x, double y, double z) {
		Vec3 eye = player.getEyePosition();
		Vec3 target = new Vec3(x, y, z);
		HitResult hit = player.getLevel().clip(new ClipContext(
			eye,
			target,
			ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE,
			player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return true;
		}
		return hit.getLocation().distanceToSqr(target) < 1.0D;
	}

	private static void sendResult(ServerPlayer player, CirclePolicy.Status status) {
		ChatFormatting color = status == CirclePolicy.Status.OK || status == CirclePolicy.Status.DISBANDED
			? ChatFormatting.AQUA : ChatFormatting.RED;
		PlayerMessages.system(player, new net.minecraft.network.chat.TranslatableComponent("circle.attuned.status."
			+ status.name().toLowerCase(Locale.ROOT)).withStyle(color));
	}

	private static void sendInviteNotice(ServerPlayer target, ServerPlayer inviter, CirclePolicy.Circle circle) {
		NetworkPackets.send(target, new CircleInvitePromptPayload(
			inviter.getUUID(),
			inviter.getName().getString(),
			circle.name(),
			circle.members().size(),
			CircleRuntime.maxMembers(),
			circle.invites().get(target.getUUID()).expiresAtTick()));
		PlayerMessages.system(target, new net.minecraft.network.chat.TranslatableComponent("circle.attuned.invited",
			inviter.getDisplayName(),
			circle.name(),
			circle.members().size(),
			CircleRuntime.maxMembers(),
			CircleRuntime.inviteTtlSeconds()).withStyle(ChatFormatting.AQUA));
	}
}
