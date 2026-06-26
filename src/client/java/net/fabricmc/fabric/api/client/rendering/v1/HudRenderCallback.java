package net.fabricmc.fabric.api.client.rendering.v1;

import dev.attuned.platform.NeoForgeEventBuses;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class HudRenderCallback {
	public static final Event EVENT = new Event();

	private HudRenderCallback() {}

	public static final class Event {
		private final AtomicInteger nextId = new AtomicInteger();

		public void register(Callback callback) {
			Objects.requireNonNull(callback, "callback");
			ResourceLocation id = new ResourceLocation("attuned",
				"hud_render_callback_" + nextId.getAndIncrement());
			NeoForgeEventBuses.modEventBus()
				.addListener((RegisterGuiLayersEvent event) ->
					event.registerAboveAll(id, callback::onHudRender));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onHudRender(GuiGraphics graphics, float tickDelta);
	}
}
