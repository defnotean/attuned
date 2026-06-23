package dev.attuned.party;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure party-effect cooldown policy.
 *
 * <p>Cooldowns are keyed by stable Circle id plus effect id, and mirrored to
 * member tombstones so leaving, hopping Circles, or disband/reform loops cannot
 * immediately refresh a shared party effect.</p>
 */
public final class CircleEffectCooldowns {
	private final Map<CircleEffectKey, Long> circleCooldowns = new HashMap<>();
	private final Map<MemberEffectKey, Long> memberTombstones = new HashMap<>();

	public void start(CirclePolicy.Circle circle, String effectId, long nowTick, long cooldownTicks) {
		Objects.requireNonNull(circle, "circle");
		String effect = normalizeEffect(effectId);
		if (cooldownTicks <= 0L) {
			clear(circle, effect);
			return;
		}
		long endsAt = saturatedAdd(nowTick, cooldownTicks);
		circleCooldowns.put(new CircleEffectKey(circle.id(), effect), endsAt);
		markMembers(circle, effect, endsAt);
	}

	public boolean ready(CirclePolicy.Circle circle, String effectId, long nowTick) {
		return remaining(circle, effectId, nowTick) == 0L;
	}

	public long remaining(CirclePolicy.Circle circle, String effectId, long nowTick) {
		Objects.requireNonNull(circle, "circle");
		String effect = normalizeEffect(effectId);
		prune(nowTick);
		long endsAt = circleCooldowns.getOrDefault(new CircleEffectKey(circle.id(), effect), 0L);
		for (UUID member : circle.members()) {
			endsAt = Math.max(endsAt,
				memberTombstones.getOrDefault(new MemberEffectKey(member, effect), 0L));
		}
		return endsAt <= nowTick ? 0L : endsAt - nowTick;
	}

	public void forgetCircle(CirclePolicy.Circle circle, long nowTick, long tombstoneTicks) {
		Objects.requireNonNull(circle, "circle");
		prune(nowTick);
		long tombstoneEndsAt = saturatedAdd(nowTick, Math.max(0L, tombstoneTicks));
		for (Map.Entry<CircleEffectKey, Long> entry : Map.copyOf(circleCooldowns).entrySet()) {
			CircleEffectKey key = entry.getKey();
			if (!key.circleId().equals(circle.id())) {
				continue;
			}
			markMembers(circle, key.effectId(), Math.max(entry.getValue(), tombstoneEndsAt));
			circleCooldowns.remove(key);
		}
	}

	public void prune(long nowTick) {
		circleCooldowns.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
		memberTombstones.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
	}

	public void clear() {
		circleCooldowns.clear();
		memberTombstones.clear();
	}

	private void markMembers(CirclePolicy.Circle circle, String effectId, long endsAt) {
		for (UUID member : circle.members()) {
			MemberEffectKey key = new MemberEffectKey(member, effectId);
			memberTombstones.put(key, Math.max(memberTombstones.getOrDefault(key, 0L), endsAt));
		}
	}

	private void clear(CirclePolicy.Circle circle, String effectId) {
		circleCooldowns.remove(new CircleEffectKey(circle.id(), effectId));
		for (UUID member : circle.members()) {
			memberTombstones.remove(new MemberEffectKey(member, effectId));
		}
	}

	private static String normalizeEffect(String effectId) {
		String normalized = Objects.requireNonNull(effectId, "effectId").trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("effectId must not be blank");
		}
		return normalized;
	}

	private static long saturatedAdd(long base, long delta) {
		if (delta <= 0L) {
			return base;
		}
		return Long.MAX_VALUE - base < delta ? Long.MAX_VALUE : base + delta;
	}

	private record CircleEffectKey(String circleId, String effectId) {
		private CircleEffectKey {
			circleId = Objects.requireNonNull(circleId, "circleId");
			effectId = Objects.requireNonNull(effectId, "effectId");
		}
	}

	private record MemberEffectKey(UUID member, String effectId) {
		private MemberEffectKey {
			member = Objects.requireNonNull(member, "member");
			effectId = Objects.requireNonNull(effectId, "effectId");
		}
	}
}
