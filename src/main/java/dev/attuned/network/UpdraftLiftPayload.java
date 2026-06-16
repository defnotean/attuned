package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server signal that the player is holding jump while gliding with
 * the Updraft Focus active. The server re-validates elytra and active Focus
 * before applying lift.
 */
public record UpdraftLiftPayload(boolean lifting) implements CustomPacketPayload {

	public static final Type<UpdraftLiftPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "updraft_lift"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UpdraftLiftPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, UpdraftLiftPayload::lifting, UpdraftLiftPayload::new);

	@Override
	public Type<UpdraftLiftPayload> type() {
		return TYPE;
	}
}
