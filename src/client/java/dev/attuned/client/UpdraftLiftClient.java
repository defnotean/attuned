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

/** Relays jump-hold while gliding to the server for {@link dev.attuned.content.behavior.UpdraftBehavior}. */
@Environment(EnvType.CLIENT)
public final class UpdraftLiftClient {
	private static boolean lastSent;
	private static boolean lastBrakeSent;
	private static boolean hasSent;
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
			lastBrakeSent = false;
			hasSent = false;
			heartbeat = 0;
			return;
		}
		boolean wantsLift = wantsLift(player);
		boolean wantsBrake = wantsBrake(player);
		if ((wantsLift || wantsBrake) && player.isFallFlying()) {
			applyLocalPrediction(player, wantsLift, wantsBrake);
		}
		boolean active = wantsLift || wantsBrake;
		heartbeat = active ? heartbeat + 1 : 0;
		boolean send = !hasSent || wantsLift != lastSent || wantsBrake != lastBrakeSent
			|| (active && heartbeat % 10 == 0);
		if (send && ClientPlayNetworking.canSend(UpdraftLiftPayload.TYPE)) {
			ClientNetworkPackets.send(new UpdraftLiftPayload(wantsLift, wantsBrake));
			lastSent = wantsLift;
			lastBrakeSent = wantsBrake;
			hasSent = true;
		}
	}

	private static boolean wantsLift(Player player) {
		if (!hasActiveUpdraft(player) || !hasFunctionalElytra(player)) {
			return false;
		}
		if (!Minecraft.getInstance().options.keyJump.isDown()) {
			return false;
		}
		return player.isFallFlying() || (!player.isOnGround() && !player.isInWater() && !player.isPassenger());
	}

	private static boolean wantsBrake(Player player) {
		return hasActiveUpdraft(player)
			&& hasFunctionalElytra(player)
			&& player.isFallFlying()
			&& Minecraft.getInstance().options.keySprint.isDown();
	}

	private static void applyLocalPrediction(Player player, boolean boosting, boolean braking) {
		player.setDeltaMovement(UpdraftBehavior.controlledMotion(
			player.getDeltaMovement(),
			player.getLookAngle(),
			player.getYRot(),
			boosting,
			braking
		));
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
