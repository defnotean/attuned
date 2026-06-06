package dev.attuned;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

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
	private static final List<Consumer<MinecraftServer>> SERVER_CALLBACKS = new ArrayList<>();
	private static boolean initialized;

	/** Registers a callback that clears transient server-lifetime state. */
	public static void onStop(Runnable cleanup) {
		CALLBACKS.add(Objects.requireNonNull(cleanup, "cleanup"));
	}

	/** Registers a callback that needs the stopping server to clear transient state. */
	public static void onStopServer(Consumer<MinecraftServer> cleanup) {
		SERVER_CALLBACKS.add(Objects.requireNonNull(cleanup, "cleanup"));
	}

	/** Registers the server-stop hook. Called from the mod initializer. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Consumer<MinecraftServer> cleanup : List.copyOf(SERVER_CALLBACKS)) {
				runServerCleanup(cleanup, server);
			}
			for (Runnable cleanup : List.copyOf(CALLBACKS)) {
				runCleanup(cleanup);
			}
		});
	}

	private static void runServerCleanup(Consumer<MinecraftServer> cleanup, MinecraftServer server) {
		try {
			cleanup.accept(server);
		} catch (RuntimeException e) {
			Attuned.LOGGER.warn("Attuned server cleanup callback failed", e);
		}
	}

	private static void runCleanup(Runnable cleanup) {
		try {
			cleanup.run();
		} catch (RuntimeException e) {
			Attuned.LOGGER.warn("Attuned server cleanup callback failed", e);
		}
	}
}
