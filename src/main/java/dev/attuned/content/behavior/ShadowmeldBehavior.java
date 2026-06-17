package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Shadowmeld Focus (Umbral Eclipse): a stealth-defense reward — while the wearer is BOTH
 * crouched AND in low light, a brief Resistance I is kept refreshed, so holding still in the
 * dark cuts incoming damage. Mirrors {@link WildwardBehavior}'s gated effect refresh: it only
 * tops the effect up when missing, and lets it lapse on its own once either gate drops.
 */
public final class ShadowmeldBehavior implements FocusBehavior {
	private static final int RESISTANCE_TICKS = 40;
	/** Light level at or below which the meld holds. */
	private static final int MAX_LIGHT = 7;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (melded(player) && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
			player.addEffect(new MobEffectInstance(
				MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 0, true, false, true));
		}
	}

	/** Both gates: crouched and in low light, by the shared darkness measure. */
	private static boolean melded(ServerPlayer player) {
		return player.isCrouching() && UmbralLight.inLowLight(player, MAX_LIGHT);
	}
}
