package net.fabricmc.fabric.api.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerTickEvents {
	public static final EndTick END_SERVER_TICK = new EndTick();

	private ServerTickEvents() {}

	public static final class EndTick {
		private final List<Consumer<MinecraftServer>> callbacks = new ArrayList<>();

		private EndTick() {
			NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
				for (Consumer<MinecraftServer> callback : List.copyOf(callbacks)) {
					callback.accept(event.getServer());
				}
			});
		}

		public void register(Consumer<MinecraftServer> callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}
}
