package dev.attuned.menu;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server signal that the player pressed the Altar GUI's Bind button. */
public record BindShardPayload() implements FabricPacket {
	public static final PacketType<BindShardPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "bind_shard"), BindShardPayload::new);

	public BindShardPayload(FriendlyByteBuf buf) {
		this();
	}

	@Override
	public void write(FriendlyByteBuf buf) {}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
