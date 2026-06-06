package dev.attuned.content.behavior;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

final class PassiveEffectRefresher {
	private PassiveEffectRefresher() {}

	static void refresh(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier,
			boolean ambient, boolean visible, boolean showIcon) {
		if (shouldRefresh(player.getEffect(effect), refreshThreshold(duration))) {
			player.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon));
		}
	}

	static boolean shouldRefresh(MobEffectInstance current, int refreshThresholdTicks) {
		return current == null || shouldRefreshDuration(current.getDuration(), refreshThresholdTicks);
	}

	static boolean shouldRefreshDuration(int currentDuration, int refreshThresholdTicks) {
		return currentDuration <= refreshThresholdTicks;
	}

	private static int refreshThreshold(int duration) {
		return Math.max(1, duration / 2);
	}
}
