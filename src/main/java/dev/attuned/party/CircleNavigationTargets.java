package dev.attuned.party;

import dev.attuned.AttunedServerCleanup;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Server-owned accepted Circle markers that passive navigation Foci may point toward. */
public final class CircleNavigationTargets {
	private static final Map<String, Target> LATEST_PINGS = new HashMap<>();
	private static boolean initialized;

	private CircleNavigationTargets() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedServerCleanup.onStop(LATEST_PINGS::clear);
	}

	public record Target(ResourceKey<Level> dimension, BlockPos pos, long createdAtTick, long expiresAtTick) {
		public Target {
			dimension = Objects.requireNonNull(dimension, "dimension");
			pos = Objects.requireNonNull(pos, "pos").immutable();
		}
	}

	public static void rememberPing(CirclePolicy.Circle circle, ResourceKey<Level> dimension,
			BlockPos pos, long createdAtTick, long expiresAtTick) {
		Objects.requireNonNull(circle, "circle");
		LATEST_PINGS.put(circle.id(), new Target(dimension, pos, createdAtTick, expiresAtTick));
	}

	public static Optional<Target> latestPingFor(ServerPlayer player, long nowTick) {
		Objects.requireNonNull(player, "player");
		Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
		if (maybeCircle.isEmpty()) {
			return Optional.empty();
		}
		CirclePolicy.Circle circle = maybeCircle.orElseThrow();
		Target target = LATEST_PINGS.get(circle.id());
		if (target == null) {
			return Optional.empty();
		}
		if (nowTick > target.expiresAtTick()) {
			LATEST_PINGS.remove(circle.id());
			return Optional.empty();
		}
		return Optional.of(target);
	}
}
