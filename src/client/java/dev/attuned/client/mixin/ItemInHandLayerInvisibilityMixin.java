package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides a hidden entity's held item. Vanilla still submits the in-hand item layer for
 * an invisible entity, leaving a sword or torch hanging in the air. This cancels the
 * item-in-hand submit pass whenever the render state is invisible to the viewer.
 *
 * <p>{@code PlayerItemInHandLayer} extends {@code ItemInHandLayer} and only overrides
 * {@code submitArmWithItem}, not {@code submit}, so injecting into the base layer's
 * {@code submit} covers players, mobs, and armor stands alike. The gate is
 * {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState#isInvisibleToPlayer}
 * (inherited by {@link ArmedEntityRenderState}), matching the living renderer's own
 * body-visibility decision so spectator/same-team visibility is preserved.</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerInvisibilityMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$hideHeldItemWhenInvisible(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int light,
			ArmedEntityRenderState state,
			float yRot,
			float xRot,
			CallbackInfo ci) {
		if (state.isInvisibleToPlayer) {
			ci.cancel();
		}
	}
}
