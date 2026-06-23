package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request for the Circle leader to kick a member. */
public record CircleKickPayload(UUID target) implements FabricPacket {
	public CircleKickPayload {
		target = target == null ? new UUID(0L, 0L) : target;
	}

	public static final PacketType<CircleKickPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_kick"), CircleKickPayload::new);

	public CircleKickPayload(FriendlyByteBuf buf) {
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
