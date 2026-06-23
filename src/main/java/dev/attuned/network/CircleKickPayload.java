package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request for the sender to kick one member from their Circle. */
public record CircleKickPayload(UUID target) implements CustomPacketPayload {
	public CircleKickPayload {
		target = Objects.requireNonNull(target, "target");
	}

	public static final Type<CircleKickPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "circle_kick"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleKickPayload> CODEC =
		StreamCodec.composite(UUIDUtil.STREAM_CODEC, CircleKickPayload::target,
			CircleKickPayload::new).cast();

	@Override
	public Type<CircleKickPayload> type() {
		return TYPE;
	}
}
