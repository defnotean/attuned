package dev.attuned.mixin;

import dev.attuned.combat.AttunedCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Applies the rock-paper-scissors affinity matchup to incoming damage.
 *
 * <p>{@code LivingEntity.hurtServer(ServerLevel, DamageSource, float)} is the
 * single server-side entry point for all damage. We rescale its {@code float}
 * amount argument at HEAD — before armour, absorption and resistance are
 * evaluated — so the matchup multiplier compounds correctly with the rest of
 * the damage pipeline. The actual matchup logic lives in
 * {@link AttunedCombat#affinityMultiplier}.</p>
 *
 * <p>{@code @ModifyVariable} with {@code argsOnly = true} targets method
 * arguments only; {@code hurtServer} has exactly one {@code float} argument, so
 * the amount is matched unambiguously by type.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float attuned$scaleDamageByAffinity(float amount, ServerLevel level, DamageSource source) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		return amount * AttunedCombat.affinityMultiplier(self, source);
	}
}
