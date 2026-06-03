package dev.attuned.mixin;

import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.RevenantCombat;
import dev.attuned.combat.UnseenCombat;
import dev.attuned.pacts.Pacts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shapes incoming damage for three Attuned systems: the rock-paper-scissors
 * affinity matchup, the Apex capstones (Bastion's damage cap, Fury's execute),
 * the Pacts set bonuses (Stoneheart's dampen, Untethered's amplifier), and
 * The Unseen's direct-melee opener.
 *
 * <p>{@code LivingEntity.hurtServer(ServerLevel, DamageSource, float)} is the
 * single server-side entry point for all damage. We rescale its {@code float}
 * amount argument at HEAD, before armour, absorption and resistance, so every
 * system compounds correctly with the rest of the damage pipeline. The matchup
 * logic lives in {@link AttunedCombat}; the capstones in {@link Apex}; the set
 * bonuses in {@link Pacts}; and stealth combat in {@link UnseenCombat}.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float attuned$adjustDamage(float amount, ServerLevel level, DamageSource source) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		float scaled = AttunedCombat.applyAffinity(level, self, source, amount);
		float capped = Apex.adjustDamage(self, source, scaled);
		float adjusted = Pacts.adjustDamage(self, source, capped);
		float unseen = UnseenCombat.adjustDamage(self, source, adjusted);
		return RevenantCombat.adjustDamage(self, source, unseen);
	}
}
