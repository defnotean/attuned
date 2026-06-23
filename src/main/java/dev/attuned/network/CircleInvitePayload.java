package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to invite one player to the sender's Circle. */
public record CircleInvitePayload(UUID target) implements CustomPacketPayload {
	public CircleInvitePayload {
		target = Objects.requireNonNull(target, "target");
	}

	public static final Type<CircleInvitePayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_invite"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleInvitePayload> CODEC =
		StreamCodec.composite(UUIDUtil.STREAM_CODEC, CircleInvitePayload::target,
			CircleInvitePayload::new).cast();

	@Override
	public Type<CircleInvitePayload> type() {
		return TYPE;
	}
}
