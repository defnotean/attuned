package dev.attuned.client;

import dev.attuned.content.AttunedContent;
import dev.attuned.content.behavior.UpdraftBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.network.UpdraftLiftPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Relays jump-hold while gliding to the server for {@link UpdraftBehavior}.
 */
@Environment(EnvType.CLIENT)
public final class UpdraftLiftClient {
	private static boolean lastSent;
	private static int heartbeat;
	private static boolean initialized;

	private UpdraftLiftClient() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(UpdraftLiftClient::tick);
	}

	private static void tick(Minecraft client) {
		Player player = client.player;
		if (client.level == null || player == null) {
			lastSent = false;
			heartbeat = 0;
			return;
		}
		boolean wantsLift = wantsLift(player);
		heartbeat = wantsLift ? heartbeat + 1 : 0;
		boolean send = wantsLift != lastSent || (wantsLift && heartbeat % 10 == 0);
		if (send && ClientPlayNetworking.canSend(UpdraftLiftPayload.TYPE)) {
			ClientPlayNetworking.send(new UpdraftLiftPayload(wantsLift));
			lastSent = wantsLift;
		}
		if (!wantsLift && lastSent) {
			lastSent = false;
		}
	}

	private static boolean wantsLift(Player player) {
		if (!hasActiveUpdraft(player) || !hasFunctionalElytra(player)) {
			return false;
		}
		if (!Minecraft.getInstance().options.keyJump.isDown()) {
			return false;
		}
		return player.isFallFlying() || (!player.onGround() && !player.isInWater() && !player.isPassenger());
	}

	private static boolean hasActiveUpdraft(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.resolution(player).activeSlots()) {
			if (inv.get(slot).is(AttunedContent.UPDRAFT_FOCUS)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasFunctionalElytra(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.is(Items.ELYTRA)) {
			return false;
		}
		return chest.getDamageValue() < chest.getMaxDamage() - 1;
	}
}
