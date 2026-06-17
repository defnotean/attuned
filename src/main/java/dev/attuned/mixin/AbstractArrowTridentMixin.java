package dev.attuned.mixin;

import dev.attuned.content.behavior.HarpoonBehavior;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Discards temporary Offshore Harpoons when 1.20.6 handles trident block hits in AbstractArrow. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowTridentMixin {
	@Shadow
	protected abstract ItemStack getPickupItem();

	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void attuned$discardTemporaryTridentAfterBlockHit(BlockHitResult hitResult, CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		if (!(arrow instanceof ThrownTrident)) {
			return;
		}
		ItemStack stack = this.getPickupItem();
		if (HarpoonBehavior.isTemporaryHarpoon(stack)) {
			arrow.discard();
		}
	}
}
