package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forgewarded Confluence behavior (Kilnward + Emberward): while near a lit forge, magma, or
 * lava, keeps a brief Fire Resistance refreshed so the wearer can work the forge unburnt. A
 * Confluence behavior carries no backing item, so it ignores the passed {@link ItemStack} and
 * holds no per-player state; the effect lapses on its own once the wearer leaves the heat or a
 * member Focus drops.
 *
 * <p>{@code Synergies} ticks this once per second; the 80-tick window outlasts that cadence so
 * the buff never flickers while the Confluence stays active and the wearer stays near heat. The
 * heat predicate mirrors {@code KilnwardBehavior#nearHeat}.</p>
 */
public final class ForgewardedBehavior implements FocusBehavior {
	private static final int FIRE_RESISTANCE_TICKS = 80;
	private static final int HEAT_RADIUS_XZ = 4;
	private static final int HEAT_RADIUS_Y = 2;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (nearHeat(player) && !player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			player.addEffect(new MobEffectInstance(
				MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0, true, false, true));
		}
	}

	/** Mirrors {@code KilnwardBehavior}'s heat predicate: any lit forge, magma, or lava nearby. */
	private static boolean nearHeat(ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-HEAT_RADIUS_XZ, -HEAT_RADIUS_Y, -HEAT_RADIUS_XZ),
				origin.offset(HEAT_RADIUS_XZ, HEAT_RADIUS_Y, HEAT_RADIUS_XZ))) {
			if (isHeat(player.level().getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isHeat(BlockState state) {
		return isLitFurnace(state)
			|| state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.LAVA)
			|| state.getFluidState().is(FluidTags.LAVA);
	}

	private static boolean isLitFurnace(BlockState state) {
		return (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
			&& state.getValue(AbstractFurnaceBlock.LIT);
	}
}
