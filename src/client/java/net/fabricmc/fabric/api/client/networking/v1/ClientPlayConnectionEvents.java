package net.fabricmc.fabric.api.client.networking.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class ClientPlayConnectionEvents {
	public static final Disconnect DISCONNECT = new Disconnect();

	private ClientPlayConnectionEvents() {}

	public static final class Disconnect {
		private final List<Callback> callbacks = new ArrayList<>();

		private Disconnect() {
			NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
				Minecraft minecraft = Minecraft.getInstance();
				ClientPacketListener handler = minecraft.getConnection();
				for (Callback callback : List.copyOf(callbacks)) {
					callback.onDisconnect(handler, minecraft);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onDisconnect(ClientPacketListener handler, Minecraft client);
	}
}
