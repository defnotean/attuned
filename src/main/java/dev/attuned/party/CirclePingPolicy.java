package dev.attuned.party;

import java.util.Objects;
import java.util.UUID;

/** Pure server-side eligibility policy for future Circle ping broadcasts. */
public final class CirclePingPolicy {
	private CirclePingPolicy() {}

	public record PingContext(UUID source, boolean sameCircle, boolean sameDimension,
			boolean targetLoaded, boolean targetVisible, double distanceSquared, long lastPingTick) {
		public PingContext {
			source = Objects.requireNonNull(source, "source");
			if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
				throw new IllegalArgumentException("distanceSquared must be a finite non-negative value");
			}
		}
	}

	public static boolean allowed(PingContext ping, long nowTick, long cooldownTicks,
			double maxDistanceBlocks) {
		Objects.requireNonNull(ping, "ping");
		if (!ping.sameCircle() || !ping.sameDimension() || !ping.targetLoaded()
				|| !ping.targetVisible()) {
			return false;
		}
		if (!Double.isFinite(maxDistanceBlocks) || maxDistanceBlocks < 0.0D) {
			return false;
		}
		double maxDistanceSquared = maxDistanceBlocks * maxDistanceBlocks;
		if (ping.distanceSquared() > maxDistanceSquared) {
			return false;
		}
		long elapsed = nowTick - ping.lastPingTick();
		return elapsed >= Math.max(0L, cooldownTicks);
	}
}
