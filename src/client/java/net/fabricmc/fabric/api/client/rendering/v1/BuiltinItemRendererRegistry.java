package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;

public final class BuiltinItemRendererRegistry {
	public static final BuiltinItemRendererRegistry INSTANCE = new BuiltinItemRendererRegistry();

	private BuiltinItemRendererRegistry() {}

	public void register(Item item, DynamicItemRenderer renderer) {
	}

	@FunctionalInterface
	public interface DynamicItemRenderer {
		void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
			MultiBufferSource bufferSource, int light, int overlay);
	}
}
