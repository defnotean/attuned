package dev.attuned.party;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-owned contribution ledger for Circle shared-credit hooks. */
public final class CircleContributions {
	private static final CircleContributionTracker TRACKER = new CircleContributionTracker();
	private static boolean initialized;

	private CircleContributions() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedPlayerCleanup.onForget(TRACKER::forget);
		AttunedServerCleanup.onStop(TRACKER::clear);
	}

	public static void recordContribution(ServerPlayer player) {
		if (!AttunedConfig.get().partySharedCreditEnabled()) {
			return;
		}
		if (CircleRuntime.manager().circleOf(player.getUUID()).isEmpty()) {
			return;
		}
		TRACKER.record(player.getUUID(), player.level().getGameTime());
	}

	public static void forgetIfMembershipChanged(CirclePolicy.Result result, ServerPlayer... players) {
		if (result.status() != CirclePolicy.Status.OK
			&& result.status() != CirclePolicy.Status.DISBANDED) {
			return;
		}
		if (players == null) {
			return;
		}
		for (ServerPlayer player : players) {
			if (player != null) {
				TRACKER.forget(player.getUUID());
			}
		}
	}

	public static java.util.List<ServerPlayer> eligibleContributors(ServerPlayer player) {
		if (!AttunedConfig.get().partySharedCreditEnabled()) {
			return List.of();
		}
		Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
		MinecraftServer server = player.level().getServer();
		if (maybeCircle.isEmpty() || server == null) {
			return List.of();
		}
		CirclePolicy.Circle circle = maybeCircle.orElseThrow();
		List<CircleContributionPolicy.MemberContext> contexts = new ArrayList<>();
		List<ServerPlayer> onlineMembers = new ArrayList<>();
		for (UUID memberId : circle.members()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			if (member == null) {
				continue;
			}
			onlineMembers.add(member);
			boolean sameDimension = member.level() == player.level();
			double distanceSquared = sameDimension ? member.distanceToSqr(player) : 0.0D;
			contexts.add(new CircleContributionPolicy.MemberContext(
				memberId,
				true,
				true,
				sameDimension,
				member.isAlive(),
				member.isSpectator(),
				distanceSquared,
				TRACKER.lastContributionTick(memberId)));
		}
		List<UUID> eligibleIds = CircleContributionPolicy.eligibleMembers(
			contexts,
			player.level().getGameTime(),
			AttunedConfig.get().partySharedCreditWindowTicks(),
			AttunedConfig.get().partySharedCreditRadius(),
			AttunedConfig.get().partyCrossDimensionCreditEnabled())
			.stream()
			.map(CircleContributionPolicy.MemberContext::member)
			.toList();
		return onlineMembers.stream()
			.filter(member -> eligibleIds.contains(member.getUUID()))
			.toList();
	}

	public static java.util.List<ServerPlayer> eligibleAssistTargets(ServerPlayer player, double radius) {
		if (!AttunedConfig.get().partySharedCreditEnabled()) {
			return List.of();
		}
		Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
		MinecraftServer server = player.level().getServer();
		if (maybeCircle.isEmpty() || server == null) {
			return List.of();
		}
		CirclePolicy.Circle circle = maybeCircle.orElseThrow();
		double effectiveRadius = Math.max(0.0D, radius);
		List<CircleContributionPolicy.MemberContext> contexts = new ArrayList<>();
		List<ServerPlayer> onlineMembers = new ArrayList<>();
		for (UUID memberId : circle.members()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			if (member == null) {
				continue;
			}
			onlineMembers.add(member);
			boolean sameDimension = member.level() == player.level();
			double distanceSquared = sameDimension ? member.distanceToSqr(player) : 0.0D;
			contexts.add(new CircleContributionPolicy.MemberContext(
				memberId,
				true,
				true,
				sameDimension,
				member.isAlive(),
				member.isSpectator(),
				distanceSquared,
				TRACKER.lastContributionTick(memberId)));
		}
		List<UUID> eligibleIds = CircleContributionPolicy.eligibleMembers(
			contexts,
			player.level().getGameTime(),
			AttunedConfig.get().partySharedCreditWindowTicks(),
			effectiveRadius,
			false)
			.stream()
			.map(CircleContributionPolicy.MemberContext::member)
			.toList();
		return onlineMembers.stream()
			.filter(member -> eligibleIds.contains(member.getUUID()))
			.toList();
	}

	public static java.util.List<ServerPlayer> nearbyCircleMembers(ServerPlayer player) {
		Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
		MinecraftServer server = player.level().getServer();
		if (maybeCircle.isEmpty() || server == null) {
			return List.of();
		}
		double radius = AttunedConfig.get().partySharedCreditRadius();
		double radiusSquared = radius * radius;
		boolean allowCrossDimension = AttunedConfig.get().partyCrossDimensionCreditEnabled();
		List<ServerPlayer> members = new ArrayList<>();
		for (UUID memberId : maybeCircle.orElseThrow().members()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			if (member == null || !member.isAlive() || member.isSpectator()) {
				continue;
			}
			boolean sameDimension = member.level() == player.level();
			if (!sameDimension && !allowCrossDimension) {
				continue;
			}
			double distanceSquared = sameDimension ? member.distanceToSqr(player) : 0.0D;
			if (distanceSquared <= radiusSquared) {
				members.add(member);
			}
		}
		return List.copyOf(members);
	}

	static void clearForTests() {
		TRACKER.clear();
	}
}
