package net.fabricmc.fabric.api.networking.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class ServerPlayConnectionEvents {
	public static final Join JOIN = new Join();
	public static final Disconnect DISCONNECT = new Disconnect();

	private ServerPlayConnectionEvents() {}

	public static final class Join {
		private final List<Callback> callbacks = new ArrayList<>();

		private Join() {
			MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
				if (!(event.getEntity() instanceof ServerPlayer player)) {
					return;
				}
				Handler handler = new Handler(player);
				for (Callback callback : List.copyOf(callbacks)) {
					callback.onPlayReady(handler, responseSender -> {}, player.server);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class Disconnect {
		private final List<DisconnectCallback> callbacks = new ArrayList<>();

		private Disconnect() {
			MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
				if (!(event.getEntity() instanceof ServerPlayer player)) {
					return;
				}
				Handler handler = new Handler(player);
				for (DisconnectCallback callback : List.copyOf(callbacks)) {
					callback.onPlayDisconnect(handler, player.server);
				}
			});
		}

		public void register(DisconnectCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class Handler {
		public final ServerPlayer player;

		public Handler(ServerPlayer player) {
			this.player = player;
		}

		public ServerPlayer player() {
			return player;
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onPlayReady(Handler handler, ServerPlayNetworking.ResponseSender sender, MinecraftServer server);
	}

	@FunctionalInterface
	public interface DisconnectCallback {
		void onPlayDisconnect(Handler handler, MinecraftServer server);
	}
}
