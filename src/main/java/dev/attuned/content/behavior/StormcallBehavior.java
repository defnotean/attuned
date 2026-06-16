package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.combat.CombatTargets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Stormcall Focus: while the wearer sprints through open rain, the sky answers —
 * lightning strikes the nearest threats every couple of seconds.
 *
 * <p>The strike timer only advances while sprinting in rain and resets the moment
 * either condition lapses, so the first bolt lands a beat after the wearer
 * commits to the run rather than instantly.
 */
public final class StormcallBehavior implements FocusBehavior {

	/** Ticks between strikes while the conditions hold (~2 seconds). */
	private static final int STRIKE_INTERVAL = 40;
	/** Search radius for hostile or PvP targets, in blocks. */
	private static final double RADIUS = 9.0;
	/** Bolts called per strike cycle. */
	private static final int BOLTS = 2;

	private final Map<UUID, Integer> ticks = new HashMap<>();

	public StormcallBehavior() {
		AttunedPlayerCleanup.onForget(ticks::remove);
		AttunedServerCleanup.onStop(ticks::clear);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		ServerLevel level = (ServerLevel) player.level();
		if (!player.isSprinting() || !isOpenRainAt(player, level)) {
			ticks.remove(id);
			return;
		}
		int t = ticks.getOrDefault(id, 0) + 1;
		if (t < STRIKE_INTERVAL) {
			ticks.put(id, t);
			return;
		}
		ticks.put(id, 0);
		strike(player, level);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticks.remove(player.getUUID());
	}

	private static boolean isOpenRainAt(ServerPlayer player, ServerLevel level) {
		return level.isRainingAt(player.blockPosition().above());
	}

	private void strike(ServerPlayer player, ServerLevel level) {
		AABB area = player.getBoundingBox().inflate(RADIUS);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, target ->
			target.isAlive() && CombatTargets.isHostileOrPvpOpponent(target, player));
		if (targets.isEmpty()) {
			return;
		}
		targets.sort(Comparator.comparingDouble(player::distanceToSqr));
		int count = Math.min(BOLTS, targets.size());
		for (int i = 0; i < count; i++) {
			LivingEntity target = targets.get(i);
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(target.getX(), target.getY(), target.getZ());
				bolt.setCause(player);
				level.addFreshEntity(bolt);
			}
		}
	}
}
