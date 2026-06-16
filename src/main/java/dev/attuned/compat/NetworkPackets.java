package dev.attuned.compat;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Small bridge from Attuned's packet records to Fabric's Minecraft 1.18 channel API. */
public final class NetworkPackets {
	private NetworkPackets() {}

	public static void send(ServerPlayer player, FabricPacket payload) {
		ServerPlayNetworking.send(player, payload.getType(), encode(payload));
	}

	public static FriendlyByteBuf encode(FabricPacket payload) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		payload.write(buf);
		return buf;
	}
}
