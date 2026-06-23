package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server request for the Circle leader to disband their current Circle. */
public record CircleDisbandPayload() implements CustomPacketPayload {

	public static final Type<CircleDisbandPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "circle_disband"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleDisbandPayload> CODEC =
		StreamCodec.unit(new CircleDisbandPayload());

	@Override
	public Type<CircleDisbandPayload> type() {
		return TYPE;
	}
}
