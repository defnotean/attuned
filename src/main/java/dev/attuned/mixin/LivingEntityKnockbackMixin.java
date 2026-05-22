package dev.attuned.mixin;

import dev.attuned.combat.Apex;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bastion's Apex capstone (Unyielding) ignores knockback entirely. Cancelling
 * {@code LivingEntity.knockback} at HEAD drops the impulse before any of the
 * knockback-resistance maths runs.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackMixin {

	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void attuned$apexKnockbackImmunity(double strength, double x, double z, CallbackInfo ci) {
		if ((Object) this instanceof Player player && Apex.ignoresKnockback(player)) {
			ci.cancel();
		}
	}
}
