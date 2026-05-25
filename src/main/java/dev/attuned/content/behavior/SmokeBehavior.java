package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Smoke Focus: the Focus-ability key creates a short misdirection burst that
 * drops nearby mob targets only when their line of sight is already broken.
 */
public final class SmokeBehavior implements FocusBehavior {
	private static final int COOLDOWN_TICKS = 300;
	private static final double RADIUS = 6.0D;

	private final Map<UUID, Long> lastSmoke = new HashMap<>();

	public SmokeBehavior() {
		AttunedPlayerCleanup.onForget(lastSmoke::remove);
	}

	@Override
	public void onAbility(ServerPlayer player, ItemStack focus) {
		long now = player.level().getGameTime();
		Long last = lastSmoke.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) {
			return;
		}
		lastSmoke.put(player.getUUID(), now);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.LARGE_SMOKE,
			player.getX(), player.getY() + 0.6, player.getZ(), 42, 0.9, 0.45, 0.9, 0.02);
		level.playSound(null, player.blockPosition(),
			SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.65F, 1.35F);
		VeilBehavior.breakVeil(player, 30);
		dropBrokenSightTargets(player, level);
	}

	private static void dropBrokenSightTargets(ServerPlayer player, ServerLevel level) {
		AABB area = player.getBoundingBox().inflate(RADIUS);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, mob ->
			mob.isAlive()
				&& mob.getTarget() == player
				&& !immuneToMisdirection(mob)
				&& !mob.hasLineOfSight(player));
		for (Mob mob : mobs) {
			mob.setTarget(null);
		}
	}

	private static boolean immuneToMisdirection(Mob mob) {
		EntityType<?> type = mob.getType();
		return type == EntityType.WARDEN || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
	}
}
