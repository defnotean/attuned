package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

public interface LevelRenderContext {
	PoseStack poseStack();

	GameRenderer gameRenderer();

	SubmitNodeCollector submitNodeCollector();
}
