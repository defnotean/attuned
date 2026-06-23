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

/** Server-to-client prompt data for accepting a pending Circle invite. */
public record CircleInvitePromptPayload(UUID inviter, String senderName, String circleName,
		int partySize, int maxMembers, long expiresAtTick) implements CustomPacketPayload {
	public CircleInvitePromptPayload {
		inviter = Objects.requireNonNull(inviter, "inviter");
		senderName = CirclePolicy.sanitizeName(senderName);
		circleName = CirclePolicy.sanitizeName(circleName);
		partySize = Math.max(0, partySize);
		maxMembers = Math.max(1, maxMembers);
	}

	public static final Type<CircleInvitePromptPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_invite_prompt"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleInvitePromptPayload> CODEC =
		StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, CircleInvitePromptPayload::inviter,
			ByteBufCodecs.STRING_UTF8, CircleInvitePromptPayload::senderName,
			ByteBufCodecs.STRING_UTF8, CircleInvitePromptPayload::circleName,
			ByteBufCodecs.INT, CircleInvitePromptPayload::partySize,
			ByteBufCodecs.INT, CircleInvitePromptPayload::maxMembers,
			ByteBufCodecs.VAR_LONG, CircleInvitePromptPayload::expiresAtTick,
			CircleInvitePromptPayload::new).cast();

	@Override
	public Type<CircleInvitePromptPayload> type() {
		return TYPE;
	}
}
