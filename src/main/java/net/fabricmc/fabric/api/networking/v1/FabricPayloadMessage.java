package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

final class FabricPayloadMessage {
	private final FabricPacket payload;

	FabricPayloadMessage(FabricPacket payload) {
		this.payload = payload;
	}

	FabricPacket payload() {
		return payload;
	}

	void encode(FriendlyByteBuf buf) {
		buf.writeResourceLocation(payload.getType().getId());
		payload.write(buf);
	}

	static FabricPayloadMessage decode(FriendlyByteBuf buf) {
		ResourceLocation id = buf.readResourceLocation();
		PacketType<?> type = PacketType.byId(id);
		if (type == null) {
			throw new IllegalStateException("Unknown Attuned packet type: " + id);
		}
		return new FabricPayloadMessage(type.read(buf));
	}
}
