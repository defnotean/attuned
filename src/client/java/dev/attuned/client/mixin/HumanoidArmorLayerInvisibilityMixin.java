package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes invisibility complete by hiding a hidden entity's worn armor. Vanilla draws
 * the base body model only when the entity is visible to the viewer, but it still
 * submits the armor layer regardless — so an invisible player (Veil, Nullveil, or a
 * vanilla Invisibility potion) keeps floating armor. This cancels the armor layer's
 * submit pass whenever the render state is invisible to the viewing player.
 *
 * <p>The gate is {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState#isInvisibleToPlayer},
 * the same per-viewer flag the living renderer uses to decide whether to draw the
 * body. Using it (rather than the raw {@code isInvisible} flag) preserves vanilla's
 * spectator/same-team "visible while invisible" behavior: teammates and spectators
 * still see both body and gear.</p>
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerInvisibilityMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$hideArmorWhenInvisible(
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
