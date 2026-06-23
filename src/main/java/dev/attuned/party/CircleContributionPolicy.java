package dev.attuned.party;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * Pure eligibility policy for future Circle shared-credit systems.
 *
 * <p>This deliberately keeps the first anti-leech rules Minecraft-free: a member must be alive,
 * online, non-spectating, in the same Circle and dimension, within radius, and recently
 * contributing. Runtime systems can decide what counts as contribution, then feed the last
 * contribution tick here.
 */
public final class CircleContributionPolicy {
	private CircleContributionPolicy() {}

	public record MemberContext(UUID member, boolean sameCircle, boolean online, boolean sameDimension,
			boolean alive, boolean spectator, double distanceSquared, long lastContributionTick) {
		public MemberContext {
			member = Objects.requireNonNull(member, "member");
			if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
				throw new IllegalArgumentException("distanceSquared must be a finite non-negative value");
			}
		}
	}

	public static boolean eligibleForSharedCredit(MemberContext member, long nowTick,
			long contributionWindowTicks, double maxDistanceBlocks) {
		return eligibleForSharedCredit(member, nowTick, contributionWindowTicks, maxDistanceBlocks, false);
	}

	public static boolean eligibleForSharedCredit(MemberContext member, long nowTick,
			long contributionWindowTicks, double maxDistanceBlocks, boolean allowCrossDimension) {
		Objects.requireNonNull(member, "member");
		if (!member.sameCircle() || !member.online() || !member.alive() || member.spectator()) {
			return false;
		}
		if (!allowCrossDimension && !member.sameDimension()) {
			return false;
		}
		if (!Double.isFinite(maxDistanceBlocks) || maxDistanceBlocks < 0.0D) {
			return false;
		}
		double maxDistanceSquared = maxDistanceBlocks * maxDistanceBlocks;
		if (member.distanceSquared() > maxDistanceSquared) {
			return false;
		}
		long elapsed = nowTick - member.lastContributionTick();
		if (elapsed < 0L) {
			return false;
		}
		return elapsed <= Math.max(0L, contributionWindowTicks);
	}

	public static List<MemberContext> eligibleMembers(List<MemberContext> candidates, long nowTick,
			long contributionWindowTicks, double maxDistanceBlocks) {
		return eligibleMembers(candidates, nowTick, contributionWindowTicks, maxDistanceBlocks, false);
	}

	public static List<MemberContext> eligibleMembers(List<MemberContext> candidates, long nowTick,
			long contributionWindowTicks, double maxDistanceBlocks, boolean allowCrossDimension) {
		Objects.requireNonNull(candidates, "candidates");
		return candidates.stream()
			.filter(candidate -> eligibleForSharedCredit(candidate, nowTick,
				contributionWindowTicks, maxDistanceBlocks, allowCrossDimension))
			.toList();
	}
}
