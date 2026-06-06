package dev.attuned.client;

import dev.attuned.network.FocusAbilityStatusPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client mirror of the server-owned Focus Ability cooldown status. */
@Environment(EnvType.CLIENT)
public final class FocusAbilityClientState {
	private static int slot = FocusAbilityStatusPayload.NO_ABILITY_SLOT;
	private static int remainingTicks;
	private static int totalTicks;

	private FocusAbilityClientState() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(FocusAbilityStatusPayload.TYPE, (payload, context) ->
			context.client().execute(() -> accept(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(FocusAbilityClientState::tick);
	}

	static void accept(FocusAbilityStatusPayload payload) {
		slot = payload.slot();
		remainingTicks = Math.max(0, payload.remainingTicks());
		totalTicks = Math.max(0, payload.totalTicks());
	}

	public static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			clear();
			return;
		}
		if (remainingTicks > 0) {
			remainingTicks--;
		}
	}

	private static void clear() {
		slot = FocusAbilityStatusPayload.NO_ABILITY_SLOT;
		remainingTicks = 0;
		totalTicks = 0;
	}

	public static int slot() {
		return slot;
	}

	public static int remainingTicks() {
		return remainingTicks;
	}

	public static int totalTicks() {
		return totalTicks;
	}

	public static float cooldownProgress() {
		if (remainingTicks <= 0 || totalTicks <= 0) {
			return 0.0F;
		}
		return Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) totalTicks));
	}
}
