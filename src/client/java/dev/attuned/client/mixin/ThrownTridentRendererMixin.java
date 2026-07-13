package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.attuned.Attuned;
import dev.attuned.AttunedThrownHarpoonEntity;
import dev.attuned.client.AttunedThrownHarpoonRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

	// The Ocean Relic GLB is 1.91 blocks long, within 1.4% of vanilla's 1.94-block
	// TridentModel, so it uses the same scale as the corrected held/charging model.
	@Unique
	private static final float ATTUNED_PROJECTILE_SCALE = 1.0F;
	@Unique
	private static final float ATTUNED_MESH_CENTER_X = 0.500F;
	@Unique
	private static final float ATTUNED_MESH_TIP_Y = 1.25932F;
	@Unique
	// Vanilla's forwardmost trident tip is 4 model pixels (0.25 blocks) ahead
	// of the projectile entity pivot. Matching that distance prevents an
	// embedded harpoon from being centred halfway through the struck block.
	private static final float VANILLA_TRIDENT_TIP_REACH = 0.25F;
	@Unique
	private static final float ATTUNED_MESH_CENTER_Z = 0.500F;

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
		// The temporary-harpoon marker lives in the server-only pickup stack, so read the synced
		// flag mirrored from vanilla's ID_FOIL pattern instead of the (client-default) trident stack.
		boolean temporaryHarpoon = ((AttunedThrownHarpoonEntity) (Object) trident).attuned$isTemporaryHarpoon();
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
		method = "submit(Lnet/minecraft/client/renderer/entity/state/ThrownTridentRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
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
		// Vanilla's flight transform expects the trident tip at -Y (the vanilla TridentModel is
		// authored prongs-down). The Ocean Relic mesh is authored prongs-up (+Y), so without this
		// 180-degree flip it flies butt-first. Flip it so the harpoon travels point-first.
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		// NONE applies Minecraft's built-in (-0.5, -0.5, -0.5) item-origin shift.
		// Centre X/Z on the projectile axis, but deliberately do not centre Y:
		// place the prong tip 0.25 blocks ahead of the entity pivot like vanilla.
		poseStack.scale(ATTUNED_PROJECTILE_SCALE, ATTUNED_PROJECTILE_SCALE, ATTUNED_PROJECTILE_SCALE);
		poseStack.translate(
			0.5F - ATTUNED_MESH_CENTER_X,
			0.5F - ATTUNED_MESH_TIP_Y + VANILLA_TRIDENT_TIP_REACH / ATTUNED_PROJECTILE_SCALE,
			0.5F - ATTUNED_MESH_CENTER_Z);
		harpoonState.attuned$item().submit(
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
		ci.cancel();
	}
}
