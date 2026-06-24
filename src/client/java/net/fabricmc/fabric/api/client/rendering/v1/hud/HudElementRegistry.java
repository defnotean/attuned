package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;

public final class HudElementRegistry {
	private HudElementRegistry() {}

	public static void attachElementAfter(Identifier after, Identifier id, HudElement renderer) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(renderer, "renderer");
		AddGuiOverlayLayersEvent.BUS.addListener(event ->
			event.getLayeredDraw().add(id, renderer::render));
	}

	@FunctionalInterface
	public interface HudElement {
		void render(GuiGraphicsExtractor graphics, DeltaTracker delta);
	}
}
