package dev.attuned.client.mixin;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla {@link BlockEntityWithoutLevelRenderer} so the Ocean Relic Trident glTF
 * renderer can delegate ordinary tridents back to their built-in 3D renderer without re-entering
 * Fabric's {@code BuiltinItemRendererRegistry} dispatch.
 */
@Mixin(ItemRenderer.class)
public interface ItemRendererBlockEntityAccessor {
	@Accessor("blockEntityRenderer")
	BlockEntityWithoutLevelRenderer attuned$blockEntityRenderer();
}
