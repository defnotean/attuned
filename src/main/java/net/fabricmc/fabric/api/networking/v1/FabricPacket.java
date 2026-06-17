package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;

/** 1.18 compatibility surface for branch-local packet records. */
public interface FabricPacket {
	void write(FriendlyByteBuf buf);

	PacketType<?> getType();
}
