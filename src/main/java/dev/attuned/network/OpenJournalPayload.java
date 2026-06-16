package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client signal that opens Attuned's custom journal screen.
 */
public record OpenJournalPayload() implements CustomPacketPayload {
	public static final Type<OpenJournalPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "open_journal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenJournalPayload> CODEC =
		StreamCodec.unit(new OpenJournalPayload());

	@Override
	public Type<OpenJournalPayload> type() {
		return TYPE;
	}
}
