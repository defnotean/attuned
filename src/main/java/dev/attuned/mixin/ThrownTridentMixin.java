package dev.attuned.mixin;

import dev.attuned.content.behavior.HarpoonBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents temporary Offshore Harpoons from becoming permanent thrown tridents. */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void attuned$discardExpiredHarpoon(CallbackInfo ci) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (HarpoonBehavior.shouldDiscardProjectile(attuned$pickupStack(),
				trident.level().getGameTime())) {
			trident.discard();
			ci.cancel();
		}
	}

	@Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
	private void attuned$blockExpiredHarpoonPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (HarpoonBehavior.shouldDiscardProjectile(attuned$pickupStack(),
				trident.level().getGameTime())) {
			trident.discard();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void attuned$discardHarpoonAfterEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
		discardAfterHit();
	}

	@Inject(method = "hitBlockEnchantmentEffects", at = @At("TAIL"))
	private void attuned$discardHarpoonAfterBlockHit(
			ServerLevel level, BlockHitResult hitResult, ItemStack weapon, CallbackInfo ci) {
		discardAfterHit();
	}

	private void discardAfterHit() {
		if (HarpoonBehavior.isTemporaryHarpoon(attuned$pickupStack())) {
			((ThrownTrident) (Object) this).discard();
		}
	}

	private ItemStack attuned$pickupStack() {
		return ((AbstractArrow) (Object) this).getPickupItemStackOrigin();
	}
}
