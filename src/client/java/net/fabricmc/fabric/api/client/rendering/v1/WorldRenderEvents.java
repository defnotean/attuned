package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorldRenderEvents {
	public static final Event END = new Event();

	private WorldRenderEvents() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void render(WorldRenderContext context);
	}
}
