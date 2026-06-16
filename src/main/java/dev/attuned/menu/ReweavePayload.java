package dev.attuned.menu;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server signal that the player pressed the Reweave button. */
public record ReweavePayload() implements FabricPacket {
	public static final PacketType<ReweavePayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "reweave"), ReweavePayload::new);

	public ReweavePayload(FriendlyByteBuf buf) {
		this();
	}

	@Override
	public void write(FriendlyByteBuf buf) {}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
