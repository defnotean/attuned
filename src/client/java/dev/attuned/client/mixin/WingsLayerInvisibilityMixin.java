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

/** Hides worn elytra when the entity is invisible to the current viewer. */
@Mixin(ElytraLayer.class)
public abstract class WingsLayerInvisibilityMixin {
	@Inject(
		method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$hideWingsWhenInvisible(
			PoseStack poseStack,
			MultiBufferSource buffer,
			int light,
			LivingEntity entity,
			float limbSwing,
			float limbSwingAmount,
			float partialTick,
			float ageInTicks,
			float netHeadYaw,
			float headPitch,
			CallbackInfo ci) {
		if (Minecraft.getInstance().player != null && entity.isInvisibleTo(Minecraft.getInstance().player)) {
			ci.cancel();
		}
	}
}
