package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.attuned.Attuned;
import dev.attuned.client.AttunedThrownHarpoonRenderState;
import dev.attuned.content.behavior.HarpoonBehavior;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownTridentRenderer.class)
public abstract class ThrownTridentRendererMixin {
	@Unique
	private static final Identifier ATTUNED_PROJECTILE_MODEL =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "ocean_relic_trident_projectile");

	@Unique
	private ItemModelResolver attuned$itemModelResolver;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void attuned$captureItemModelResolver(EntityRendererProvider.Context context, CallbackInfo ci) {
		this.attuned$itemModelResolver = context.getItemModelResolver();
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;Lnet/minecraft/client/renderer/entity/state/ThrownTridentRenderState;F)V",
		at = @At("TAIL"))
	private void attuned$extractTemporaryHarpoonState(
			ThrownTrident trident, ThrownTridentRenderState state, float tickProgress, CallbackInfo ci) {
		AttunedThrownHarpoonRenderState harpoonState = (AttunedThrownHarpoonRenderState) state;
		ItemStack pickupStack = trident.getPickupItemStackOrigin();
		boolean temporaryHarpoon = HarpoonBehavior.isTemporaryHarpoon(pickupStack);
		harpoonState.attuned$setTemporaryHarpoon(temporaryHarpoon);
		harpoonState.attuned$item().clear();
		if (!temporaryHarpoon) {
			return;
		}

		ItemStack projectileStack = pickupStack.copyWithCount(1);
		projectileStack.set(DataComponents.ITEM_MODEL, ATTUNED_PROJECTILE_MODEL);
		this.attuned$itemModelResolver.updateForNonLiving(
			harpoonState.attuned$item(), projectileStack, ItemDisplayContext.NONE, trident);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/ThrownTridentRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void attuned$submitTemporaryHarpoon(
			ThrownTridentRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			CameraRenderState cameraRenderState,
			CallbackInfo ci) {
		AttunedThrownHarpoonRenderState harpoonState = (AttunedThrownHarpoonRenderState) state;
		if (!harpoonState.attuned$isTemporaryHarpoon()) {
			return;
		}

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
		poseStack.scale(0.62F, 0.62F, 0.62F);
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		harpoonState.attuned$item().submit(
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			0,
			state.outlineColor);
		poseStack.popPose();
		ci.cancel();
	}
}
