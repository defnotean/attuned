package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class ClientTickEvents {
	public static final EndTick END_CLIENT_TICK = new EndTick();

	private ClientTickEvents() {}

	public static final class EndTick {
		private final List<Consumer<Minecraft>> callbacks = new ArrayList<>();

		private EndTick() {
			MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> {
				Minecraft minecraft = Minecraft.getInstance();
				for (Consumer<Minecraft> callback : List.copyOf(callbacks)) {
					callback.accept(minecraft);
				}
			});
		}

		public void register(Consumer<Minecraft> callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}
}
