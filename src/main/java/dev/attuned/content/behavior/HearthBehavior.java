package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hearth Focus: near a lit campfire, a well-fed wearer slowly recovers health.
 */
public final class HearthBehavior implements FocusBehavior {
	private static final int CHECK_INTERVAL = 80;
	private static final int DURATION = 80;
	private static final int MIN_FOOD = 12;
	private static final int RADIUS_XZ = 4;
	private static final int RADIUS_Y = 2;

	private final Map<UUID, Integer> ticks = new HashMap<>();

	public HearthBehavior() {
		AttunedPlayerCleanup.onForget(ticks::remove);
		AttunedServerCleanup.onStop(ticks::clear);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		int tick = ticks.getOrDefault(id, 0) + 1;
		if (tick < CHECK_INTERVAL) {
			ticks.put(id, tick);
			return;
		}
		ticks.put(id, 0);
		if (player.getHealth() >= player.getMaxHealth()
				|| player.getFoodData().getFoodLevel() < MIN_FOOD
				|| !nearLitCampfire(player)) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 0, true, false, true));
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticks.remove(player.getUUID());
	}

	private static boolean nearLitCampfire(ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-RADIUS_XZ, -RADIUS_Y, -RADIUS_XZ),
				origin.offset(RADIUS_XZ, RADIUS_Y, RADIUS_XZ))) {
			BlockState state = player.getLevel().getBlockState(pos);
			if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
					&& state.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}
}
