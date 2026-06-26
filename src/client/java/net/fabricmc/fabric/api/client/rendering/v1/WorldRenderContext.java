package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;

public interface WorldRenderContext {
	PoseStack matrixStack();

	GameRenderer gameRenderer();

	default Object consumers() {
		return null;
	}
}
