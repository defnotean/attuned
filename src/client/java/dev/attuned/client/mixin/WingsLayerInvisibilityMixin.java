package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1: hides worn elytra on an entity that is invisible to the viewing player.
 * Vanilla renders elytra for invisible entities, so this cancels the layer render pass.
 */
@Mixin(ElytraLayer.class)
public abstract class WingsLayerInvisibilityMixin {
	@Inject(
		method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$hideWingsWhenInvisible(
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			LivingEntity entity,
			float limbSwing,
			float limbSwingAmount,
			float partialTick,
			float ageInTicks,
			float netHeadYaw,
			float headPitch,
			CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player != null && entity.isInvisibleTo(player)) {
			ci.cancel();
		}
	}
}
