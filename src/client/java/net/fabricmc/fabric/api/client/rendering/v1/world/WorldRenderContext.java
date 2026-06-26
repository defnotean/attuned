package net.fabricmc.fabric.api.client.rendering.v1.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public record WorldRenderContext(RenderLevelStageEvent event) {
	public PoseStack matrices() {
		return event.getPoseStack();
	}

	public GameRenderer gameRenderer() {
		return Minecraft.getInstance().gameRenderer;
	}

	public MultiBufferSource.BufferSource consumers() {
		return Minecraft.getInstance().renderBuffers().bufferSource();
	}
}
