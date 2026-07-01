package dev.attuned.mixin;

import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.CombatContext;
import dev.attuned.combat.RevenantCombat;
import dev.attuned.combat.UnseenCombat;
import dev.attuned.content.behavior.UpdraftBehavior;
import dev.attuned.pacts.Pacts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
 * <p>{@code LivingEntity.hurt(DamageSource, float)} is the 1.21.1 damage entry
 * point. We rescale its {@code float}
 * amount argument at HEAD, before armour, absorption and resistance, so every
 * system compounds correctly with the rest of the damage pipeline. The matchup
 * logic lives in {@link AttunedCombat}; the capstones in {@link Apex}; the set
 * bonuses in {@link Pacts}; and stealth combat in {@link UnseenCombat}.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float attuned$adjustDamage(float amount, DamageSource source) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self.level() instanceof ServerLevel level)) {
			return amount;
		}
		UpdraftBehavior.recordPvpDamage(self, source);
		// Updraft fall-damage mitigation is the first stage of the single ordered
		// pipeline (previously a separate LivingEntityUpdraftFallMixin whose
		// relative order with this mixin was left to Mixin's class-name sort). It
		// short-circuits before the matchup/apex/pact/stealth stages, which do not
		// apply to fall damage anyway.
		if (source.is(DamageTypes.FALL) && self instanceof ServerPlayer player
				&& UpdraftBehavior.mitigatesFallDamage(player)) {
			return 0.0F;
		}
		CombatContext context = CombatContext.of(self, source);
		// Confluence or party role damage hooks must join this ordered pipeline,
		// consuming the previous stage's output so independent bonuses compound.
		float scaled = AttunedCombat.applyAffinity(level, self, source, amount, context);
		float capped = Apex.adjustDamage(self, source, scaled, context);
		float adjusted = Pacts.adjustDamage(self, source, capped, context);
		float unseen = UnseenCombat.adjustDamage(self, source, adjusted);
		return RevenantCombat.adjustDamage(self, source, unseen);
	}
}
