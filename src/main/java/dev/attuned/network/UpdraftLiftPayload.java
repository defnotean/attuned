package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server Updraft Focus flight control state. */
public record UpdraftLiftPayload(boolean boosting, boolean braking) implements CustomPacketPayload {
	public static final Type<UpdraftLiftPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "updraft_lift"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UpdraftLiftPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.BOOL, UpdraftLiftPayload::boosting,
			ByteBufCodecs.BOOL, UpdraftLiftPayload::braking,
			UpdraftLiftPayload::new
		);

	@Override
	public Type<UpdraftLiftPayload> type() {
		return TYPE;
	}
}
