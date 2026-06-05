package dev.attuned;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Coordinates teardown of transient static state when a server instance stops.
 *
 * <p>Integrated-client testing and singleplayer world switches can leave mod
 * classes loaded while the server object is replaced. Runtime caches that are
 * keyed by UUIDs, game-time, or live entity references should register here so
 * the next server starts from a clean in-memory slate.
 */
public final class AttunedServerCleanup {
	private AttunedServerCleanup() {}

	private static final List<Runnable> CALLBACKS = new ArrayList<>();
	private static boolean initialized;

	/** Registers a callback that clears transient server-lifetime state. */
	public static void onStop(Runnable cleanup) {
		CALLBACKS.add(Objects.requireNonNull(cleanup, "cleanup"));
	}

	/** Registers the server-stop hook. Called from the mod initializer. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Runnable cleanup : CALLBACKS) {
				cleanup.run();
			}
		});
	}
}
