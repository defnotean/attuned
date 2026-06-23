package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to leave the sender's current Circle. */
public record CircleLeavePayload() implements FabricPacket {
	public static final PacketType<CircleLeavePayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_leave"), CircleLeavePayload::new);

	public CircleLeavePayload(FriendlyByteBuf buf) {
		this();
	}

	@Override
	public void write(FriendlyByteBuf buf) {}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
