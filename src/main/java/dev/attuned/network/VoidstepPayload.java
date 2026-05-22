package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server signal that the player performed the Voidstep gesture — a
 * double-tapped jump while sneaking. It carries no data; the server resolves the
 * teleport entirely from the player's own state.
 */
public record VoidstepPayload() implements CustomPacketPayload {

	public static final Type<VoidstepPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "voidstep"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VoidstepPayload> CODEC =
		StreamCodec.unit(new VoidstepPayload());

	@Override
	public Type<VoidstepPayload> type() {
		return TYPE;
	}
}
