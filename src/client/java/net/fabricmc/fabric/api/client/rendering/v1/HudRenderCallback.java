package net.fabricmc.fabric.api.client.rendering.v1;

import dev.attuned.platform.NeoForgeEventBuses;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class HudRenderCallback {
	public static final Event EVENT = new Event();

	private HudRenderCallback() {}

	public static final class Event {
		private final AtomicInteger nextId = new AtomicInteger();

		public void register(Callback callback) {
			Objects.requireNonNull(callback, "callback");
			Identifier id = Identifier.fromNamespaceAndPath("attuned",
				"hud_render_callback_" + nextId.getAndIncrement());
			NeoForgeEventBuses.modEventBus()
				.addListener((RegisterGuiLayersEvent event) ->
					event.registerAboveAll(id, callback::onHudRender));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker delta);
	}
}
