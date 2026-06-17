package dev.attuned.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.minecraft.network.FriendlyByteBuf;

/** Client-side bridge from Attuned's packet records to Fabric's Minecraft 1.18 channel API. */
public final class ClientNetworkPackets {
	private ClientNetworkPackets() {}

	public static void send(FabricPacket payload) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		payload.write(buf);
		ClientPlayNetworking.send(payload.getType(), buf);
	}
}
