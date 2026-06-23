package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server request to accept a pending Circle invite from one inviter. */
public record CircleAcceptPayload(UUID inviter) implements CustomPacketPayload {
	public CircleAcceptPayload {
		inviter = Objects.requireNonNull(inviter, "inviter");
	}

	public static final Type<CircleAcceptPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "circle_accept"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleAcceptPayload> CODEC =
		StreamCodec.composite(UUIDUtil.STREAM_CODEC, CircleAcceptPayload::inviter,
			CircleAcceptPayload::new).cast();

	@Override
	public Type<CircleAcceptPayload> type() {
		return TYPE;
	}
}
