package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.combat.CombatTargets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Lantern Focus: torchlight in darkness briefly marks visible nearby hostiles.
 */
public final class LanternBehavior implements FocusBehavior {
	private static final int CHECK_INTERVAL = 20;
	private static final int MAX_LIGHT = 7;
	private static final int GLOW_DURATION = 30;
	private static final double RADIUS = 8.0;

	private final Map<UUID, Integer> ticks = new HashMap<>();

	public LanternBehavior() {
		AttunedPlayerCleanup.onForget(ticks::remove);
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
		if (!holdsLight(player) || player.level().getMaxLocalRawBrightness(player.blockPosition()) > MAX_LIGHT) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		AABB area = player.getBoundingBox().inflate(RADIUS);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, target ->
			target.isAlive() && CombatTargets.isHostileOrPvpOpponent(target, player)
				&& player.hasLineOfSight(target));
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION, 0, true, false, false));
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticks.remove(player.getUUID());
	}

	private static boolean holdsLight(ServerPlayer player) {
		return isLightItem(player.getMainHandItem().getItem())
			|| isLightItem(player.getOffhandItem().getItem());
	}

	private static boolean isLightItem(Item item) {
		return item == Items.TORCH
			|| item == Items.SOUL_TORCH
			|| item == Items.LANTERN
			|| item == Items.SOUL_LANTERN;
	}
}
