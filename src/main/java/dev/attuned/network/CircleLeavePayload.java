package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request for the sender to leave their current Circle. */
public record CircleLeavePayload() implements CustomPacketPayload {

	public static final Type<CircleLeavePayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_leave"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleLeavePayload> CODEC =
		StreamCodec.unit(new CircleLeavePayload());

	@Override
	public Type<CircleLeavePayload> type() {
		return TYPE;
	}
}
