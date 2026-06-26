package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;

public final class HudRenderCallback {
	public static final Event EVENT = new Event();

	private HudRenderCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {
			MinecraftForge.EVENT_BUS.addListener(this::onRender);
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}

		private void onRender(RenderGuiEvent.Post event) {
			for (Callback callback : List.copyOf(callbacks)) {
				callback.onHudRender(event.getGuiGraphics(), event.getPartialTick());
			}
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onHudRender(GuiGraphics graphics, float tickDelta);
	}
}
