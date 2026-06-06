package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Harborlight Focus: a gentle night-vision utility for players carrying a
 * lantern near water. It has no combat effect.
 */
public final class HarborlightBehavior implements FocusBehavior {
	private static final int CHECK_INTERVAL = 40;
	private static final int DURATION = 80;
	private static final int MAX_LIGHT = 10;

	private final Map<UUID, Integer> ticks = new HashMap<>();

	public HarborlightBehavior() {
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
		if (!nearWater(player)) {
			return;
		}
		if (!holdsLantern(player) || player.level().getMaxLocalRawBrightness(player.blockPosition()) > MAX_LIGHT) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, true, false, false));
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticks.remove(player.getUUID());
	}

	private static boolean holdsLantern(ServerPlayer player) {
		return isLantern(player.getMainHandItem().getItem()) || isLantern(player.getOffhandItem().getItem());
	}

	private static boolean isLantern(Item item) {
		return item == Items.LANTERN || item == Items.SOUL_LANTERN;
	}

	private static boolean nearWater(ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		return player.level().getFluidState(pos).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.below()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.north()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.south()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.east()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.west()).is(FluidTags.WATER);
	}
}
