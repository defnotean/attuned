package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client prompt for a pending Circle invite. */
public record CircleInvitePromptPayload(UUID sender, String senderName, String circleName,
		int partySize, int maxMembers, long expiresAtTick) implements FabricPacket {
	public CircleInvitePromptPayload {
		sender = sender == null ? new UUID(0L, 0L) : sender;
		senderName = CirclePolicy.sanitizeName(senderName);
		circleName = CirclePolicy.sanitizeName(circleName);
		partySize = Math.max(0, partySize);
		maxMembers = Math.max(1, maxMembers);
	}

	public static final PacketType<CircleInvitePromptPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_invite_prompt"),
			CircleInvitePromptPayload::new);

	public CircleInvitePromptPayload(FriendlyByteBuf buf) {
		this(buf.readUUID(),
			buf.readUtf(CirclePolicy.MAX_NAME_LENGTH),
			buf.readUtf(CirclePolicy.MAX_NAME_LENGTH),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readVarLong());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUUID(sender);
		buf.writeUtf(senderName);
		buf.writeUtf(circleName);
		buf.writeVarInt(partySize);
		buf.writeVarInt(maxMembers);
		buf.writeVarLong(expiresAtTick);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
