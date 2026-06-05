package dev.attuned;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Coordinates teardown of transient per-player state when a player disconnects.
 *
 * <p>Several Attuned systems cache per-player data in {@code Map<UUID, ?>}
 * fields — tick counters, cooldown timestamps, the active-Focus snapshot. None
 * of it needs to persist, and a player who logs out while a Focus is active is
 * never ticked again, so the matching {@code onDeactivate} never runs and the
 * entries would linger for the lifetime of the server. Effects that temporarily
 * changed the player can register through {@link #onForgetPlayer}; owners that
 * only need to drop cached state can register through {@link #onForget}.
 */
public final class AttunedPlayerCleanup {
	private AttunedPlayerCleanup() {}

	private static final List<Consumer<UUID>> CALLBACKS = new ArrayList<>();
	private static final List<Consumer<ServerPlayer>> PLAYER_CALLBACKS = new ArrayList<>();
	private static boolean initialized;

	/** Registers a callback that drops the given player's cached transient state. */
	public static void onForget(Consumer<UUID> cleanup) {
		CALLBACKS.add(Objects.requireNonNull(cleanup, "cleanup"));
	}

	/** Registers a callback that can restore transient state on the player before it is forgotten. */
	public static void onForgetPlayer(Consumer<ServerPlayer> cleanup) {
		PLAYER_CALLBACKS.add(Objects.requireNonNull(cleanup, "cleanup"));
	}

	/** Registers the disconnect hook. Called from the mod initializer. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			for (Consumer<ServerPlayer> cleanup : PLAYER_CALLBACKS) {
				cleanup.accept(handler.player);
			}
			UUID id = handler.player.getUUID();
			for (Consumer<UUID> cleanup : CALLBACKS) {
				cleanup.accept(id);
			}
		});
	}
}
