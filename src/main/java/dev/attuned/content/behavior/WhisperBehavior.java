package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
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

/** Whisper Focus: ability-key hush that softens detection for a short window. */
public final class WhisperBehavior implements FocusBehavior {
	private static final int COOLDOWN_TICKS = 300;
	private static final int SOFTEN_TICKS = 80;
	private static final double RADIUS = 8.0D;

	private static final Map<UUID, Integer> ACTIVE_WINDOWS = new HashMap<>();

	public WhisperBehavior() {
		AttunedPlayerCleanup.onForget(ACTIVE_WINDOWS::remove);
		AttunedServerCleanup.onStop(ACTIVE_WINDOWS::clear);
	}

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return COOLDOWN_TICKS;
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		ACTIVE_WINDOWS.put(player.getUUID(), SOFTEN_TICKS);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.SMOKE,
			player.getX(), player.getY() + 0.55D, player.getZ(), 8, 0.35D, 0.2D, 0.35D, 0.005D);
		level.playSound(null, player.blockPosition(),
			SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.3F, 1.8F);
		softenDetection(player, level);
		return true;
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		int remaining = ACTIVE_WINDOWS.getOrDefault(player.getUUID(), 0);
		if (remaining <= 0) {
			return;
		}
		softenDetection(player, (ServerLevel) player.level());
		if (remaining == 1) {
			ACTIVE_WINDOWS.remove(player.getUUID());
		} else {
			ACTIVE_WINDOWS.put(player.getUUID(), remaining - 1);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ACTIVE_WINDOWS.remove(player.getUUID());
	}

	private static void softenDetection(ServerPlayer player, ServerLevel level) {
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
