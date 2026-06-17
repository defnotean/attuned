package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server signal that the player pressed the Focus Ability keybind. It
 * carries no data; the server resolves the one active ability Focus entirely
 * from the player's own active Foci, so a forged payload triggers nothing extra.
 */
public record AbilityPayload() implements CustomPacketPayload {

	public static final Type<AbilityPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "ability"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> CODEC =
		StreamCodec.unit(new AbilityPayload());

	@Override
	public Type<AbilityPayload> type() {
		return TYPE;
	}
}
