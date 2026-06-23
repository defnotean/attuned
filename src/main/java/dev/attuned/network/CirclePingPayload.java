package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to place a transient Circle location marker. */
public record CirclePingPayload(double x, double y, double z) implements CustomPacketPayload {
	public CirclePingPayload {
		requireFinite(x, "x");
		requireFinite(y, "y");
		requireFinite(z, "z");
	}

	public static final Type<CirclePingPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_ping"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CirclePingPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.DOUBLE, CirclePingPayload::x,
			ByteBufCodecs.DOUBLE, CirclePingPayload::y,
			ByteBufCodecs.DOUBLE, CirclePingPayload::z,
			CirclePingPayload::new).cast();

	@Override
	public Type<CirclePingPayload> type() {
		return TYPE;
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
