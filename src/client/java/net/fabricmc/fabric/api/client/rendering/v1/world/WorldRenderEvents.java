package net.fabricmc.fabric.api.client.rendering.v1.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class WorldRenderEvents {
	public static final Event END_MAIN = new Event();

	private WorldRenderEvents() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();
		private boolean hooked;

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
			if (!hooked) {
				hooked = true;
				NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel event) -> {
					WorldRenderContext context = new WorldRenderContext(event);
					for (Callback registered : List.copyOf(callbacks)) {
						registered.render(context);
					}
				});
			}
		}
	}

	@FunctionalInterface
	public interface Callback {
		void render(WorldRenderContext context);
	}
}
