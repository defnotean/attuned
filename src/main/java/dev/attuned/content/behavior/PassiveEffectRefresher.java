package dev.attuned.content.behavior;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

final class PassiveEffectRefresher {
	private static final boolean PASSIVE_AMBIENT = true;
	private static final boolean PASSIVE_VISIBLE = false;
	private static final boolean PASSIVE_SHOW_ICON = false;

	private PassiveEffectRefresher() {}

	static void refresh(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
		refresh(player, effect, duration, amplifier, refreshThreshold(duration));
	}

	static void refresh(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier,
			int refreshThresholdTicks) {
		refresh(player, effect, duration, amplifier, refreshThresholdTicks,
			PASSIVE_AMBIENT, PASSIVE_VISIBLE, PASSIVE_SHOW_ICON);
	}

	static void refresh(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier,
			boolean ambient, boolean visible, boolean showIcon) {
		refresh(player, effect, duration, amplifier, refreshThreshold(duration), ambient, visible, showIcon);
	}

	static void apply(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, duration, amplifier,
			PASSIVE_AMBIENT, PASSIVE_VISIBLE, PASSIVE_SHOW_ICON));
	}

	private static void refresh(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier,
			int refreshThresholdTicks, boolean ambient, boolean visible, boolean showIcon) {
		if (shouldRefresh(player.getEffect(effect), refreshThresholdTicks, amplifier)) {
			player.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon));
		}
	}

	static boolean shouldRefresh(MobEffectInstance current, int refreshThresholdTicks) {
		return shouldRefresh(current, refreshThresholdTicks, 0);
	}

	static boolean shouldRefresh(MobEffectInstance current, int refreshThresholdTicks, int amplifier) {
		if (current == null) {
			return true;
		}
		return shouldRefreshState(current.getAmplifier(), current.getDuration(), refreshThresholdTicks, amplifier);
	}

	static boolean shouldRefreshState(int currentAmplifier, int currentDuration, int refreshThresholdTicks,
			int targetAmplifier) {
		if (currentAmplifier < targetAmplifier) {
			return true;
		}
		return currentAmplifier == targetAmplifier
			&& shouldRefreshDuration(currentDuration, refreshThresholdTicks);
	}

	static boolean shouldRefreshDuration(int currentDuration, int refreshThresholdTicks) {
		return currentDuration <= refreshThresholdTicks;
	}

	private static int refreshThreshold(int duration) {
		return Math.max(1, duration / 2);
	}
}
