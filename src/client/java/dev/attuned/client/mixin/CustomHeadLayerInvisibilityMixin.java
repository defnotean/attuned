package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the worn-head equipment layer on a hidden entity — the layer that renders a
 * worn skull, a block-on-head, or any item equipped in the head slot. Vanilla keeps
 * submitting it for invisible entities, so a head-worn item would otherwise float.
 * This cancels the worn-head submit pass whenever the render state is invisible to
 * the viewer.
 *
 * <p>The gate is {@link LivingEntityRenderState#isInvisibleToPlayer}, the same
 * per-viewer flag the living renderer uses for the body, so spectator/same-team
 * visibility still shows the worn head.</p>
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerInvisibilityMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$hideWornHeadWhenInvisible(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int light,
			LivingEntityRenderState state,
			float yRot,
			float xRot,
			CallbackInfo ci) {
		if (state.isInvisibleToPlayer) {
			ci.cancel();
		}
	}
}
