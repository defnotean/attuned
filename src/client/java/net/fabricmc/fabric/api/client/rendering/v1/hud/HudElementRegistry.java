package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class HudElementRegistry {
	private HudElementRegistry() {}

	public static void attachElementAfter(ResourceLocation after, ResourceLocation id, HudElement renderer) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(renderer, "renderer");
	}

	@FunctionalInterface
	public interface HudElement {
		void render(GuiGraphics graphics, float tickDelta);
	}
}
