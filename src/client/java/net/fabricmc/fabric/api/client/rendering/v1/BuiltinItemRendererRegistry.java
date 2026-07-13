package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.attuned.client.mixin.ItemClientExtensionsAccessor;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public enum BuiltinItemRendererRegistry {
	INSTANCE;

	public void register(Item item, DynamicItemRenderer renderer) {
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(renderer, "renderer");
		// Forge always sends held tridents through IClientItemExtensions. Install an
		// extension on the vanilla item so this compatibility registry is a real
		// bridge instead of silently discarding Fabric-style registrations.
		((ItemClientExtensionsAccessor) (Object) item).attuned$setRenderProperties(new IClientItemExtensions() {
			private BlockEntityWithoutLevelRenderer customRenderer;

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				if (this.customRenderer == null) {
					Minecraft minecraft = Minecraft.getInstance();
					this.customRenderer = new BlockEntityWithoutLevelRenderer(
						minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels()) {
						@Override
						public void renderByItem(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
								MultiBufferSource vertexConsumers, int light, int overlay) {
							renderer.render(stack, mode, matrices, vertexConsumers, light, overlay);
						}
					};
				}
				return this.customRenderer;
			}
		});
	}

	@FunctionalInterface
	public interface DynamicItemRenderer {
		void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
				MultiBufferSource vertexConsumers, int light, int overlay);
	}
}
