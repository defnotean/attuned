package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to create a server-owned Attuned Circle. */
public record CircleCreatePayload(String name) implements CustomPacketPayload {
	public CircleCreatePayload {
		name = CirclePolicy.sanitizeName(name);
	}

	public static final Type<CircleCreatePayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_create"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleCreatePayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CircleCreatePayload::name,
			CircleCreatePayload::new).cast();

	@Override
	public Type<CircleCreatePayload> type() {
		return TYPE;
	}
}
