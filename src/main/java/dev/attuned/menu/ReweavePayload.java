package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server signal that the Reweave button was pressed. */
public record ReweavePayload() implements CustomPacketPayload {
	public static final Type<ReweavePayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "reweave"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ReweavePayload> CODEC =
		StreamCodec.unit(new ReweavePayload());

	@Override
	public Type<ReweavePayload> type() {
		return TYPE;
	}
}
