package dev.attuned.network;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Attuned networking: the Focus-ability channel.
 *
 * <p>When a player presses the ability keybind the client sends an
 * {@link AbilityPayload}; the server then triggers {@link FocusBehavior#onAbility}
 * on every one of that player's active Foci. The payload carries no data and all
 * validation is server-side, so a forged payload only ever fires the abilities
 * the player legitimately has.
 */
public final class AttunedNetworking {
	private AttunedNetworking() {}

	/** Registers the ability payload type and its server-side receiver. */
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> triggerAbilities(player));
		});
	}

	private static void triggerAbilities(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inv.get(slot);
			FocusBehavior behavior = Attunement.definitionFor(player, stack)
				.flatMap(FocusDefinition::behavior)
				.map(AttunedRegistries::getBehavior)
				.orElse(null);
			if (behavior != null) {
				behavior.onAbility(player, stack);
			}
		}
	}
}
