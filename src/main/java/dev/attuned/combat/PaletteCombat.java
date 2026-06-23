package dev.attuned.combat;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.behavior.DataFocusBehaviors.MarkedTargetBehavior;
import dev.attuned.content.behavior.DataFocusBehaviors.OnHitEffectBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Routes datapack {@code attuned:on_hit_effect} palette behaviors through the existing combat
 * {@code AFTER_DAMAGE} handler — no mixin, no new event. {@link AttunedCombat#afterDamage} calls
 * {@link #onMeleeHit} next to the Thornward/Leech procs; this walks the attacker's active Foci,
 * finds any carrying an {@link OnHitEffectBehavior}, and applies its effect when the per-Focus
 * charge and target gates pass. Those gates reuse the single authoritative combat predicates
 * ({@link AttunedCombat#isChargedDirectMelee} and {@link CombatTargets#isHostileOrPvpOpponent})
 * so a data behavior can never proc on a swing the code path would reject.
 */
public final class PaletteCombat {
	private PaletteCombat() {}

	/**
	 * Applies every active on-hit palette behavior the attacker carries to one landed hit.
	 * {@code dealtDamage} is the damage actually taken after armour and absorption.
	 */
	public static void onMeleeHit(LivingEntity attacker, LivingEntity defender,
			DamageSource source, float dealtDamage) {
		if (dealtDamage <= 0.0F || !(attacker instanceof ServerPlayer player)) {
			return;
		}
		AttunedInv inventory = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			FocusBehavior behavior = Attunement.definitionFor(player, stack)
				.flatMap(FocusDefinition::behavior)
				.map(behaviorId -> AttunedRegistries.getBehavior(behaviorId, player.registryAccess()))
				.orElse(null);
			if (behavior instanceof OnHitEffectBehavior onHit) {
				if (!AttunedCombat.isChargedDirectMelee(player, defender, source, onHit.chargeThreshold())) {
					continue;
				}
				if (onHit.hostileOnly() && !CombatTargets.isHostileOrPvpOpponent(defender, player)) {
					continue;
				}
				onHit.applyTo(player, defender);
			} else if (behavior instanceof MarkedTargetBehavior markedTarget) {
				if (!AttunedCombat.isChargedDirectMelee(player, defender, source, markedTarget.chargeThreshold())) {
					continue;
				}
				if (!CombatTargets.isHostileOrPvpOpponent(defender, player)) {
					continue;
				}
				long now = player.level().getGameTime();
				markedTarget.onChargedMeleeHit(player, defender, now);
			}
		}
	}
}
