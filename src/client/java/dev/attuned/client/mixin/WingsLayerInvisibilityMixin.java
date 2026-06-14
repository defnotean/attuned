package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides a hidden entity's worn elytra/wings. The wings layer renders the elytra
 * equipped in the chest slot; vanilla keeps submitting it for invisible entities, so
 * a worn elytra would otherwise float behind nothing. This cancels the wings submit
 * pass whenever the render state is invisible to the viewer.
 *
 * <p>The gate is {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState#isInvisibleToPlayer}
 * (inherited by {@link HumanoidRenderState}), matching the living renderer's body
 * decision so spectator/same-team visibility still shows the elytra.</p>
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerInvisibilityMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$hideWingsWhenInvisible(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int light,
			HumanoidRenderState state,
			float yRot,
			float xRot,
			CallbackInfo ci) {
		if (state.isInvisibleToPlayer) {
			ci.cancel();
		}
	}
}
