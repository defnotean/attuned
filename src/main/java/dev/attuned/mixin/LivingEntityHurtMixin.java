package dev.attuned.mixin;

import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shapes incoming damage for two Attuned systems: the rock-paper-scissors
 * affinity matchup and the Apex capstones (Bastion's damage cap, Fury's execute).
 *
 * <p>{@code LivingEntity.hurtServer(ServerLevel, DamageSource, float)} is the
 * single server-side entry point for all damage. We rescale its {@code float}
 * amount argument at HEAD — before armour, absorption and resistance — so both
 * systems compound correctly with the rest of the damage pipeline. The matchup
 * logic lives in {@link AttunedCombat}; the capstones in {@link Apex}.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float attuned$adjustDamage(float amount, ServerLevel level, DamageSource source) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		float scaled = amount * AttunedCombat.affinityMultiplier(self, source);
		return Apex.adjustDamage(self, source, scaled);
	}
}
