package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

public record WorldRenderContext(Camera camera, PoseStack matrixStack, MultiBufferSource.BufferSource consumers) {
	public static WorldRenderContext empty() {
		Minecraft minecraft = Minecraft.getInstance();
		return new WorldRenderContext(
			minecraft.gameRenderer.getMainCamera(),
			new PoseStack(),
			minecraft.renderBuffers().bufferSource());
	}
}
