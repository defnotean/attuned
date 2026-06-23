package dev.attuned.party;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure server-owned ledger of recent Circle contribution ticks. */
public final class CircleContributionTracker {
	private static final long NEVER_CONTRIBUTED = Long.MIN_VALUE / 4L;
	private final Map<UUID, Long> lastContributionTicks = new HashMap<>();

	public void record(UUID member, long nowTick) {
		member = Objects.requireNonNull(member, "member");
		lastContributionTicks.merge(member, nowTick, Math::max);
	}

	public long lastContributionTick(UUID member) {
		return lastContributionTicks.getOrDefault(Objects.requireNonNull(member, "member"),
			NEVER_CONTRIBUTED);
	}

	public void forget(UUID member) {
		lastContributionTicks.remove(Objects.requireNonNull(member, "member"));
	}

	public void prune(long nowTick, long contributionWindowTicks) {
		long window = Math.max(0L, contributionWindowTicks);
		lastContributionTicks.entrySet().removeIf(entry -> nowTick - entry.getValue() > window);
	}

	public void clear() {
		lastContributionTicks.clear();
	}
}
