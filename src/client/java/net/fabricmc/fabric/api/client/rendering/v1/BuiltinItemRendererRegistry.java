package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public enum BuiltinItemRendererRegistry {
	INSTANCE;

	public void register(Item item, DynamicItemRenderer renderer) {
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(renderer, "renderer");
	}

	@FunctionalInterface
	public interface DynamicItemRenderer {
		void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
				Object vertexConsumers, int light, int overlay);
	}
}
