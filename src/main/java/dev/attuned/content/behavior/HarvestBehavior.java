package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Harvest Focus: standing among crops coaxes them along — roughly once a second
 * a few unripe crops within range receive an extra growth tick.
 *
 * <p>Each pass nudges at most {@link #MAX_PER_PASS} crops, and only with a low
 * per-crop chance, so the boost reads as "slightly faster" rather than instant
 * bonemeal.
 */
public final class HarvestBehavior implements FocusBehavior {

	/** Ticks between growth passes (~1 second). */
	private static final int GROW_INTERVAL = 20;
	/** Search radius for crops, in blocks. */
	private static final int RADIUS = 5;
	/** Crops nudged per pass — kept low so the boost stays gentle. */
	private static final int MAX_PER_PASS = 3;

	private final Map<UUID, Integer> ticks = new HashMap<>();

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		int t = ticks.getOrDefault(id, 0) + 1;
		if (t < GROW_INTERVAL) {
			ticks.put(id, t);
			return;
		}
		ticks.put(id, 0);
		growNearbyCrops(player);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticks.remove(player.getUUID());
	}

	private void growNearbyCrops(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		RandomSource random = level.getRandom();
		BlockPos center = player.blockPosition();
		int grown = 0;
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-RADIUS, -RADIUS, -RADIUS),
				center.offset(RADIUS, RADIUS, RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
					&& random.nextInt(6) == 0) {
				state.randomTick(level, pos.immutable(), random);
				if (++grown >= MAX_PER_PASS) {
					return;
				}
			}
		}
	}
}
