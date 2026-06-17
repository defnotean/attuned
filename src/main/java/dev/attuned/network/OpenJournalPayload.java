package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client signal that opens Attuned's custom journal screen. */
public record OpenJournalPayload() implements FabricPacket {
	public static final PacketType<OpenJournalPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "open_journal"), OpenJournalPayload::new);

	public OpenJournalPayload(FriendlyByteBuf buf) {
		this();
	}

	@Override
	public void write(FriendlyByteBuf buf) {}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
