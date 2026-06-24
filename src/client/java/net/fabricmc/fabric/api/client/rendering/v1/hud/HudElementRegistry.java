package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class HudElementRegistry {
	private HudElementRegistry() {}

	public static void attachElementAfter(ResourceLocation after, ResourceLocation id, HudElement renderer) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(renderer, "renderer");
		FMLJavaModLoadingContext.get().getModEventBus()
			.addListener((AddGuiOverlayLayersEvent event) ->
				event.getLayeredDraw().add(id, renderer::render));
	}

	@FunctionalInterface
	public interface HudElement {
		void render(GuiGraphics graphics, DeltaTracker delta);
	}
}
