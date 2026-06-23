package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to create a short-lived Circle navigation ping. */
public record CirclePingPayload(double x, double y, double z) implements FabricPacket {
	public CirclePingPayload {
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("Circle ping coordinates must be finite");
		}
	}

	public static final PacketType<CirclePingPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_ping"), CirclePingPayload::new);

	public CirclePingPayload(FriendlyByteBuf buf) {
		this(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
