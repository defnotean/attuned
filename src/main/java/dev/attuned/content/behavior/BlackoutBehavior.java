package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Blackout Focus: a compact Unseen smoke pulse that briefly blinds and drops
 * nearby mob targets. It is intentionally smaller than Smoke Focus.
 */
public final class BlackoutBehavior implements FocusBehavior {
	private static final int COOLDOWN_TICKS = 240;
	private static final int BLACKOUT_TICKS = 40;
	private static final double RADIUS = 3.5D;

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
		ServerLevel level = (ServerLevel) player.getLevel();
		level.sendParticles(ParticleTypes.SMOKE,
			player.getX(), player.getY() + 0.7D, player.getZ(),
			24, 0.55D, 0.3D, 0.55D, 0.02D);
		level.playSound(null, player.blockPosition(),
			SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.45F, 1.75F);
		VeilBehavior.breakVeil(player, BLACKOUT_TICKS);
		disruptTargets(player, level);
		return true;
	}

	private static void disruptTargets(ServerPlayer player, ServerLevel level) {
		AABB area = player.getBoundingBox().inflate(RADIUS);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, mob ->
			mob.isAlive()
				&& mob.getTarget() == player
				&& !immuneToMisdirection(mob));
		for (Mob mob : mobs) {
			mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
				BLACKOUT_TICKS, 0, true, false, false));
			mob.setTarget(null);
		}
	}

	private static boolean immuneToMisdirection(Mob mob) {
		EntityType<?> type = mob.getType();
		return type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
	}
}
