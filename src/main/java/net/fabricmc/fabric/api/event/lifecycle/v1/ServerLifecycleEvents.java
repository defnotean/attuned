package net.fabricmc.fabric.api.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ServerLifecycleEvents {
	public static final ServerStopping SERVER_STOPPING = new ServerStopping();
	public static final EndDataPackReload END_DATA_PACK_RELOAD = new EndDataPackReload();

	private ServerLifecycleEvents() {}

	public static final class ServerStopping {
		private final List<Consumer<MinecraftServer>> callbacks = new ArrayList<>();

		private ServerStopping() {
			ServerStoppingEvent.BUS.addListener(event -> {
				for (Consumer<MinecraftServer> callback : List.copyOf(callbacks)) {
					callback.accept(event.getServer());
				}
			});
		}

		public void register(Consumer<MinecraftServer> callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class EndDataPackReload {
		private final List<Callback> callbacks = new ArrayList<>();

		private EndDataPackReload() {
			TagsUpdatedEvent.BUS.addListener(event -> {
				if (!event.shouldUpdateStaticData()) {
					return;
				}
				MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
				if (server == null) {
					return;
				}
				for (Callback callback : List.copyOf(callbacks)) {
					callback.onEndDataPackReload(server, null, true);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onEndDataPackReload(MinecraftServer server, ResourceManager resourceManager, boolean success);
	}
}
