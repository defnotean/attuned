package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server request to inspect another player's public attunement state.
 */
public record InspectRequestPayload(int targetEntityId) implements FabricPacket {
	public static final PacketType<InspectRequestPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "inspect_request"), InspectRequestPayload::new);

	public InspectRequestPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(targetEntityId);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
