package dev.attuned.compat;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft 1.18 fallback for last-death position, which was not exposed by ServerPlayer yet. */
public final class LastDeathPositions {
	private static final Map<UUID, GlobalPos> LAST_DEATH = new HashMap<>();
	private static boolean initialized;

	private LastDeathPositions() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				LAST_DEATH.put(player.getUUID(), GlobalPos.of(player.getLevel().dimension(), player.blockPosition()));
			}
		});
		AttunedPlayerCleanup.onForget(LAST_DEATH::remove);
		AttunedServerCleanup.onStop(LAST_DEATH::clear);
	}

	public static Optional<GlobalPos> get(ServerPlayer player) {
		init();
		return Optional.ofNullable(LAST_DEATH.get(player.getUUID()));
	}
}
