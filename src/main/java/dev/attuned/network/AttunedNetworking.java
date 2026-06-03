package dev.attuned.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Attuned networking: the Focus Ability channel.
 *
 * <p>When a player presses the ability keybind the client sends an
 * {@link AbilityPayload}; the server then triggers {@link FocusBehavior#onAbility}
 * on the first active Focus that explicitly owns the ability key. The payload
 * carries no data and all validation is server-side, so a forged payload only
 * ever fires the single ability the player legitimately has active.
 */
public final class AttunedNetworking {
	private AttunedNetworking() {}

	/** Registers the ability payload type and its server-side receiver. */
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> FocusAbilityState.trigger(player));
		});
		FocusAbilityState.init();
	}
}
