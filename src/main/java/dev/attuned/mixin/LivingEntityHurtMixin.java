package dev.attuned.mixin;

import dev.attuned.compat.AfterDamageCallback;
import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.CombatContext;
import dev.attuned.combat.RevenantCombat;
import dev.attuned.combat.UnseenCombat;
import dev.attuned.content.behavior.UpdraftBehavior;
import dev.attuned.pacts.Pacts;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shapes incoming damage for three Attuned systems: the rock-paper-scissors
 * affinity matchup, the Apex capstones (Bastion's damage cap, Fury's execute),
 * the Pacts set bonuses (Stoneheart's dampen, Untethered's amplifier), and
 * The Unseen's direct-melee opener.
 *
 * <p>{@code LivingEntity.hurt(DamageSource, float)} is the 1.20.6
 * server-side entry point for all damage. We rescale its {@code float}
 * amount argument at HEAD, before armour, absorption and resistance, so every
 * system compounds correctly with the rest of the damage pipeline. The matchup
 * logic lives in {@link AttunedCombat}; the capstones in {@link Apex}; the set
 * bonuses in {@link Pacts}; and stealth combat in {@link UnseenCombat}.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
	@Unique
	private float attuned$beforeDamagePool;

	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void attuned$captureDamagePool(DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getLevel() instanceof ServerLevel
				&& !ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(self, source, amount)) {
			cir.setReturnValue(false);
			return;
		}
		attuned$beforeDamagePool = self.getHealth() + self.getAbsorptionAmount();
	}

	@Inject(
		method = "hurt",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;die(Lnet/minecraft/world/damagesource/DamageSource;)V"),
		cancellable = true
	)
	private void attuned$allowDeath(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getLevel() instanceof ServerLevel
				&& !ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(self, source, amount)) {
			cir.setReturnValue(false);
		}
	}

	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float attuned$adjustDamage(float amount, DamageSource source) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self.getLevel() instanceof ServerLevel level)) {
			return amount;
		}
		UpdraftBehavior.recordPvpDamage(self, source);
		CombatContext context = CombatContext.of(self, source);
		float scaled = AttunedCombat.applyAffinity(level, self, source, amount, context);
		float capped = Apex.adjustDamage(self, source, scaled, context);
		float adjusted = Pacts.adjustDamage(self, source, capped, context);
		float unseen = UnseenCombat.adjustDamage(self, source, adjusted);
		return RevenantCombat.adjustDamage(self, source, unseen);
	}

	@Inject(method = "hurt", at = @At("RETURN"))
	private void attuned$afterDamage(DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self.getLevel() instanceof ServerLevel)) {
			return;
		}
		float afterDamagePool = self.getHealth() + self.getAbsorptionAmount();
		float dealtDamage = Math.max(0.0F, attuned$beforeDamagePool - afterDamagePool);
		if (dealtDamage <= 0.0F) {
			return;
		}
		AfterDamageCallback.EVENT.invoker().afterDamage(self, source, amount, dealtDamage, false);
	}

	@Inject(method = "die", at = @At("TAIL"))
	private void attuned$afterDeath(DamageSource source, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getLevel() instanceof ServerLevel) {
			ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(self, source);
		}
	}
}
