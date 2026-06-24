package net.fabricmc.fabric.api.client.networking.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;

public final class ClientPlayConnectionEvents {
	public static final Disconnect DISCONNECT = new Disconnect();

	private ClientPlayConnectionEvents() {}

	public static final class Disconnect {
		private final List<Callback> callbacks = new ArrayList<>();

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}

		public void fire(Object handler) {
			for (Callback callback : List.copyOf(callbacks)) {
				callback.onDisconnect(handler, Minecraft.getInstance());
			}
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onDisconnect(Object handler, Minecraft client);
	}
}
