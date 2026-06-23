package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client bounded location marker for Circle members. */
public record CirclePingClientPayload(UUID source, String sourceName, double x, double y,
		double z, long expiresAtTick) implements CustomPacketPayload {
	public CirclePingClientPayload {
		source = Objects.requireNonNull(source, "source");
		sourceName = CirclePolicy.sanitizeName(sourceName);
		requireFinite(x, "x");
		requireFinite(y, "y");
		requireFinite(z, "z");
	}

	public static final Type<CirclePingClientPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_ping_marker"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CirclePingClientPayload> CODEC =
		StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, CirclePingClientPayload::source,
			ByteBufCodecs.STRING_UTF8, CirclePingClientPayload::sourceName,
			ByteBufCodecs.DOUBLE, CirclePingClientPayload::x,
			ByteBufCodecs.DOUBLE, CirclePingClientPayload::y,
			ByteBufCodecs.DOUBLE, CirclePingClientPayload::z,
			ByteBufCodecs.VAR_LONG, CirclePingClientPayload::expiresAtTick,
			CirclePingClientPayload::new).cast();

	@Override
	public Type<CirclePingClientPayload> type() {
		return TYPE;
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
