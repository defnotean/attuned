package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lodestone Focus: draws nearby dropped items toward the wearer each tick, like a
 * gentle magnet. Items still have to be picked up normally — the Focus only nudges
 * them within reach.
 */
public final class LodestoneBehavior implements FocusBehavior {

	/** Radius, in blocks, within which dropped items are pulled. */
	private static final double RANGE = 5.0;

	/** Speed of the pull, in blocks per tick, applied toward the player. */
	private static final double PULL_SPEED = 0.18;

	/** Below this squared distance an item is close enough; no nudge needed. */
	private static final double STOP_DISTANCE_SQR = 0.7 * 0.7;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		// Throttle the entity query to every other tick (mirrors Epitaph's % 2
		// gate); the pull stays smooth at half the polling cost.
		if (player.tickCount % 2 != 0) {
			return;
		}
		AABB area = player.getBoundingBox().inflate(RANGE);
		List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, area);
		Vec3 target = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (ItemEntity item : items) {
			// Skip items still in their post-drop pickup delay (e.g. the player's
			// own just-dropped stack) so the Focus does not fight the toss.
			if (!item.isAlive() || item.hasPickUpDelay()) {
				continue;
			}
			Vec3 toPlayer = target.subtract(item.position());
			if (toPlayer.lengthSqr() < STOP_DISTANCE_SQR) {
				continue;
			}
			item.setDeltaMovement(toPlayer.normalize().scale(PULL_SPEED));
		}
	}
}
