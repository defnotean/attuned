package dev.attuned.content.behavior;

import dev.attuned.combat.CombatFeedback;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusCondition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/** Pearlguard Focus: wet resistance and a brief absorption shell on demand. */
public final class PearlguardBehavior implements FocusBehavior {
	private static final FocusCondition.InRain WET = new FocusCondition.InRain();
	private static final int RESIST_DURATION_TICKS = 60;
	private static final int ABSORPTION_TICKS = 60;
	private static final int ABILITY_COOLDOWN_TICKS = 400;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (!WET.test(player)) {
			return;
		}
		PassiveEffectRefresher.refresh(player, MobEffects.RESISTANCE,
			RESIST_DURATION_TICKS, 0, true, false, false);
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
			MobEffects.ABSORPTION, ABSORPTION_TICKS, 0, true, false, true));
		CombatFeedback.abilityCast(player, CombatFeedback.AbilityFlavor.TIDE);
		return true;
	}
}
