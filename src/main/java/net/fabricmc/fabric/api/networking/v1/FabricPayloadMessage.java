package net.fabricmc.fabric.api.networking.v1;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class FabricPayloadMessage {
	private final PacketType<?> type;
	private final FabricPacket payload;
	private final byte[] payloadBytes;

	FabricPayloadMessage(FabricPacket payload) {
		this.type = payload.getType();
		this.payload = payload;
		this.payloadBytes = null;
	}

	FabricPayloadMessage(PacketType<?> type, FriendlyByteBuf payloadBytes) {
		this.type = type;
		this.payload = null;
		this.payloadBytes = copyBytes(payloadBytes);
	}

	public FabricPacket payload() {
		if (payload != null) {
			return payload;
		}
		return type.read(rawBuffer());
	}

	public PacketType<?> type() {
		return type;
	}

	public FriendlyByteBuf rawBuffer() {
		if (payloadBytes != null) {
			return new FriendlyByteBuf(Unpooled.wrappedBuffer(payloadBytes));
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		payload.write(buf);
		return buf;
	}

	void encode(FriendlyByteBuf buf) {
		buf.writeResourceLocation(type.getId());
		if (payload != null) {
			payload.write(buf);
		} else {
			buf.writeBytes(payloadBytes);
		}
	}

	static FabricPayloadMessage decode(FriendlyByteBuf buf) {
		ResourceLocation id = buf.readResourceLocation();
		PacketType<?> type = PacketType.byId(id);
		if (type == null) {
			throw new IllegalStateException("Unknown Attuned packet type: " + id);
		}
		return new FabricPayloadMessage(type, buf);
	}

	private static byte[] copyBytes(FriendlyByteBuf buf) {
		byte[] data = new byte[buf.readableBytes()];
		buf.readBytes(data);
		return data;
	}
}
