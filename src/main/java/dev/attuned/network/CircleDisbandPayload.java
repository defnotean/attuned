package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request for the Circle leader to disband the Circle. */
public record CircleDisbandPayload() implements FabricPacket {
	public static final PacketType<CircleDisbandPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_disband"), CircleDisbandPayload::new);

	public CircleDisbandPayload(FriendlyByteBuf buf) {
		this();
	}

	@Override
	public void write(FriendlyByteBuf buf) {}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
