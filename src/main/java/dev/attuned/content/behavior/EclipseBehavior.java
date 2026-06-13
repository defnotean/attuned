package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Eclipse Focus (Umbral Eclipse): the signature offensive Focus — while the wearer stands in
 * TOTAL darkness (light level at or below {@value #MAX_LIGHT}), Strength I is kept refreshed,
 * so committing to the deep dark pays off in damage. A stricter darkness gate than the rest of
 * the set, mirroring {@link WildwardBehavior}'s gated, self-lapsing effect refresh.
 */
public final class EclipseBehavior implements FocusBehavior {
	private static final int STRENGTH_TICKS = 40;
	/** Light level at or below which total darkness reigns. */
	private static final int MAX_LIGHT = 4;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (UmbralLight.inLowLight(player, MAX_LIGHT) && !player.hasEffect(MobEffects.STRENGTH)) {
			player.addEffect(new MobEffectInstance(
				MobEffects.STRENGTH, STRENGTH_TICKS, 0, true, false, true));
		}
	}
}
