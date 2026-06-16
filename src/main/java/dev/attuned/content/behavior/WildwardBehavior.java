package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wildward Confluence behavior (Mossheart + Rootstep): while standing on natural ground,
 * keeps a brief Resistance I refreshed. A Confluence behavior carries no backing item, so
 * it ignores the passed {@link ItemStack} entirely and is purely stateless — the effect
 * itself is the only persistent footprint, and it lapses on its own once the wearer leaves
 * natural ground or a member Focus drops.
 *
 * <p>{@code Synergies} ticks this once per second; the 60-tick window comfortably outlasts
 * that cadence so the buff never flickers while the Confluence stays active.</p>
 */
public final class WildwardBehavior implements FocusBehavior {
	private static final int RESISTANCE_TICKS = 60;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (onGreenFooting(player) && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
			player.addEffect(new MobEffectInstance(
				MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 0, true, false, true));
		}
	}

	/** Mirrors {@code MossheartBehavior}'s ground predicate: green footing at feet or below. */
	private static boolean onGreenFooting(ServerPlayer player) {
		BlockState feet = player.getLevel().getBlockState(player.blockPosition());
		BlockState below = player.getLevel().getBlockState(player.blockPosition().below());
		return isGreenFooting(feet) || isGreenFooting(below);
	}

	private static boolean isGreenFooting(BlockState state) {
		return state.is(BlockTags.LEAVES)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.MOSS_CARPET);
	}
}
