package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to create a server-owned Attuned Circle. */
public record CircleCreatePayload(String name) implements FabricPacket {
	public CircleCreatePayload {
		name = CirclePolicy.sanitizeName(name);
	}

	public static final PacketType<CircleCreatePayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_create"), CircleCreatePayload::new);

	public CircleCreatePayload(FriendlyByteBuf buf) {
		this(buf.readUtf(CirclePolicy.MAX_NAME_LENGTH));
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
