package dev.attuned.content.behavior;

import dev.attuned.combat.CombatFeedback;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/** Sparkweld Focus: steady Haste and a brief Strength surge on demand. */
public final class SparkweldBehavior implements FocusBehavior {
	private static final int HASTE_DURATION_TICKS = 80;
	private static final int HASTE_REFRESH_TICKS = 80;
	private static final int STRENGTH_TICKS = 100;
	private static final int ABILITY_COOLDOWN_TICKS = 240;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (player.tickCount % HASTE_REFRESH_TICKS != 0) {
			return;
		}
		PassiveEffectRefresher.refresh(player, MobEffects.DIG_SPEED,
			HASTE_DURATION_TICKS, 0, true, false, false);
	}

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return ABILITY_COOLDOWN_TICKS;
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		player.addEffect(new MobEffectInstance(
			MobEffects.DAMAGE_BOOST, STRENGTH_TICKS, 0, true, false, true));
		CombatFeedback.abilityCast(player, CombatFeedback.AbilityFlavor.FORGE);
		return true;
	}
}
