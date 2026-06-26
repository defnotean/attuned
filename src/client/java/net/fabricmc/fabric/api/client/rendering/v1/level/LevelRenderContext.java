package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

public interface LevelRenderContext {
	PoseStack poseStack();

	GameRenderer gameRenderer();

	default MultiBufferSource.BufferSource bufferSource() {
		return Minecraft.getInstance().renderBuffers().bufferSource();
	}
}
