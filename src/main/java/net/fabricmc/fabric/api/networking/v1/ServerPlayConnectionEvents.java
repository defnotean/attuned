package net.fabricmc.fabric.api.networking.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ServerPlayConnectionEvents {
	public static final Join JOIN = new Join();
	public static final Disconnect DISCONNECT = new Disconnect();

	private ServerPlayConnectionEvents() {}

	public static final class Join {
		private final List<JoinCallback> callbacks = new ArrayList<>();

		private Join() {
			NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
				if (!(event.getEntity() instanceof ServerPlayer player)) {
					return;
				}
				MinecraftServer server = player.level().getServer();
				PacketSender sender = new PacketSender();
				for (JoinCallback callback : List.copyOf(callbacks)) {
					callback.onPlayReady(player.connection, sender, server);
				}
			});
		}

		public void register(JoinCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class Disconnect {
		private final List<DisconnectCallback> callbacks = new ArrayList<>();

		private Disconnect() {
			NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
				if (!(event.getEntity() instanceof ServerPlayer player)) {
					return;
				}
				MinecraftServer server = player.level().getServer();
				for (DisconnectCallback callback : List.copyOf(callbacks)) {
					callback.onPlayDisconnect(player.connection, server);
				}
			});
		}

		public void register(DisconnectCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class PacketSender {}

	@FunctionalInterface
	public interface JoinCallback {
		void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
	}

	@FunctionalInterface
	public interface DisconnectCallback {
		void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
	}
}
