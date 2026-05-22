package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Tide Focus: lets the wearer breathe underwater while the Focus is active. The
 * Focus also carries a declarative water-movement-efficiency modifier so the
 * wearer moves freely while submerged.
 *
 * <p>Each server tick a short, ambient Water Breathing effect is refreshed so it
 * never lapses while equipped, and clears on its own shortly after the Focus is
 * removed. The effect is hidden and icon-less to keep it unobtrusive.
 */
public final class TideBehavior implements FocusBehavior {

	/** Refreshed duration in ticks — comfortably longer than the refresh interval. */
	private static final int DURATION = 40;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		player.addEffect(new MobEffectInstance(
			MobEffects.WATER_BREATHING, DURATION, 0, true, false, false));
	}
}
