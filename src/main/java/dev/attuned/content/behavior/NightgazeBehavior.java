package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Nightgaze Focus: grants night vision while the Focus is active.
 *
 * <p>Each server tick a short, ambient Night Vision effect is refreshed so it
 * never lapses while equipped, and clears on its own shortly after the Focus is
 * removed. The effect is hidden and icon-less to keep it unobtrusive.
 */
public final class NightgazeBehavior implements FocusBehavior {

	/** Refreshed duration in ticks — comfortably longer than the refresh interval. */
	private static final int DURATION = 40;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		player.addEffect(new MobEffectInstance(
			MobEffects.NIGHT_VISION, DURATION, 0, true, false, false));
	}
}
