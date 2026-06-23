package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to accept a pending Circle invite. */
public record CircleAcceptPayload(UUID inviter) implements FabricPacket {
	public CircleAcceptPayload {
		inviter = inviter == null ? new UUID(0L, 0L) : inviter;
	}

	public static final PacketType<CircleAcceptPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_accept"), CircleAcceptPayload::new);

	public CircleAcceptPayload(FriendlyByteBuf buf) {
		this(buf.readUUID());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUUID(inviter);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
