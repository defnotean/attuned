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

	// The Ocean Relic GLB is ~1.9 blocks tall (origin at the butt). Scale it down to a trident-sized
	// projectile and recentre it on the mesh centroid; tuned to match the in-hand mesh size.
	@Unique
	private static final float ATTUNED_PROJECTILE_SCALE = 0.54F;
	@Unique
	private static final float ATTUNED_MESH_CENTER_X = 0.003F;
	@Unique
	private static final float ATTUNED_MESH_CENTER_Y = 0.923F;
	@Unique
	private static final float ATTUNED_MESH_CENTER_Z = 0.019F;

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
		// The temporary-harpoon marker lives in the server-only pickup stack, so read the synced
		// flag mirrored from vanilla's ID_FOIL pattern instead of the (client-default) trident stack.
		boolean temporaryHarpoon = ((AttunedThrownHarpoonEntity) (Object) trident).attuned$isTemporaryHarpoon();
		harpoonState.attuned$setTemporaryHarpoon(temporaryHarpoon);
		harpoonState.attuned$item().clear();
		if (!temporaryHarpoon) {
			return;
		}

		ItemStack projectileStack = trident.getPickupItemStackOrigin().copyWithCount(1);
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
		// Vanilla's flight transform expects the trident tip at -Y (the vanilla TridentModel is
		// authored prongs-down). The Ocean Relic mesh is authored prongs-up (+Y), so without this
		// 180-degree flip it flies butt-first. Flip it so the harpoon travels point-first.
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		// The mesh is authored in block units with its origin at the butt: pivot about its centre so
		// it tracks the projectile instead of swinging around the handle.
		poseStack.scale(ATTUNED_PROJECTILE_SCALE, ATTUNED_PROJECTILE_SCALE, ATTUNED_PROJECTILE_SCALE);
		poseStack.translate(-ATTUNED_MESH_CENTER_X, -ATTUNED_MESH_CENTER_Y, -ATTUNED_MESH_CENTER_Z);
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
