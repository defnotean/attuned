package dev.attuned.mixin;

import dev.attuned.content.behavior.UpdraftBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Prevents fall damage while Updraft boost is active with a functional elytra. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUpdraftFallMixin {

	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float attuned$mitigateUpdraftFall(float amount, DamageSource source) {
		if (amount <= 0.0F || !source.is(DamageTypes.FALL)) {
			return amount;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player && UpdraftBehavior.mitigatesFallDamage(player)) {
			return 0.0F;
		}
		return amount;
	}
}
