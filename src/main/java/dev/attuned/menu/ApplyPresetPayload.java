package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ApplyPresetPayload(int index) implements CustomPacketPayload {
	public ApplyPresetPayload {
		if (index < 0) {
			index = -1;
		}
	}

	public static final Type<ApplyPresetPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "apply_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPresetPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, ApplyPresetPayload::index, ApplyPresetPayload::new).cast();

	@Override
	public Type<ApplyPresetPayload> type() {
		return TYPE;
	}
}
