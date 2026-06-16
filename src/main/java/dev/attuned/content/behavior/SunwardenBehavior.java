package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Sunwarden Confluence behavior (Votive + Bellwether): while standing in bright light, keeps
 * a brief Regeneration I refreshed — the two Radiant Foci nursing the wearer's wounds in the
 * light. A Confluence behavior carries no backing item, so it ignores the passed
 * {@link ItemStack} and holds no per-player state; the effect lapses on its own when the
 * wearer steps into shadow or a member Focus drops.
 *
 * <p>{@code Synergies} ticks this once per second; the 60-tick window outlasts that cadence so
 * the buff never flickers while the Confluence stays active and the wearer stays lit.</p>
 */
public final class SunwardenBehavior implements FocusBehavior {
	private static final int BRIGHT_LIGHT = 12;
	private static final int REGEN_TICKS = 60;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (isBright(player) && !player.hasEffect(MobEffects.REGENERATION)) {
			player.addEffect(new MobEffectInstance(
				MobEffects.REGENERATION, REGEN_TICKS, 0, true, false, true));
		}
	}

	private static boolean isBright(ServerPlayer player) {
		return player.getLevel().getMaxLocalRawBrightness(player.blockPosition()) >= BRIGHT_LIGHT;
	}
}
