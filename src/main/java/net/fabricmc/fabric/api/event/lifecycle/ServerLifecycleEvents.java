package net.fabricmc.fabric.api.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class ServerLifecycleEvents {
	public static final ServerStopping SERVER_STOPPING = new ServerStopping();
	public static final EndDataPackReload END_DATA_PACK_RELOAD = new EndDataPackReload();

	private ServerLifecycleEvents() {}

	public static final class ServerStopping {
		private final List<Consumer<MinecraftServer>> callbacks = new ArrayList<>();

		private ServerStopping() {
			MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
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
		public void register(Callback callback) {
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onEndDataPackReload(MinecraftServer server, ResourceManager resourceManager, boolean success);
	}
}
