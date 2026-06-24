package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class HudRenderCallback {
	public static final Event EVENT = new Event();

	private HudRenderCallback() {}

	public static final class Event {
		private final AtomicInteger nextId = new AtomicInteger();

		public void register(Callback callback) {
			Objects.requireNonNull(callback, "callback");
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("attuned",
				"hud_render_callback_" + nextId.getAndIncrement());
			FMLJavaModLoadingContext.get().getModEventBus()
				.addListener((AddGuiOverlayLayersEvent event) ->
					event.getLayeredDraw().add(id, callback::onHudRender));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onHudRender(GuiGraphics graphics, DeltaTracker delta);
	}
}
