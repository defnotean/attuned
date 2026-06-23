package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to invite another player to the sender's Circle. */
public record CircleInvitePayload(UUID target) implements FabricPacket {
	public CircleInvitePayload {
		target = target == null ? new UUID(0L, 0L) : target;
	}

	public static final PacketType<CircleInvitePayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_invite"), CircleInvitePayload::new);

	public CircleInvitePayload(FriendlyByteBuf buf) {
		this(buf.readUUID());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUUID(target);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
