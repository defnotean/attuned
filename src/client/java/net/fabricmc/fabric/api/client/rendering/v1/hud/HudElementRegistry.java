package net.fabricmc.fabric.api.client.rendering.v1.hud;

import dev.attuned.platform.NeoForgeEventBuses;
import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class HudElementRegistry {
	private HudElementRegistry() {}

	public static void attachElementAfter(Identifier after, Identifier id, HudElement renderer) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(renderer, "renderer");
		NeoForgeEventBuses.modEventBus()
			.addListener((RegisterGuiLayersEvent event) ->
				event.registerAbove(after, id, renderer::render));
	}

	@FunctionalInterface
	public interface HudElement {
		void render(GuiGraphics graphics, DeltaTracker delta);
	}
}
