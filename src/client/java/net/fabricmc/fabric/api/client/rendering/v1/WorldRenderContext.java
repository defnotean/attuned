package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

public interface WorldRenderContext {
	PoseStack matrixStack();

	GameRenderer gameRenderer();

	default MultiBufferSource.BufferSource consumers() {
		return Minecraft.getInstance().renderBuffers().bufferSource();
	}
}
