package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;

public interface FabricPacket {
	void write(FriendlyByteBuf buf);

	PacketType<?> getType();
}
