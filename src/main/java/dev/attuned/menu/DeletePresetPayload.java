package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeletePresetPayload(int index, String name) implements CustomPacketPayload {
	public DeletePresetPayload {
		if (index < 0) {
			index = -1;
		}
		name = FocusPreset.sanitizeName(name);
	}

	public static final Type<DeletePresetPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "delete_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeletePresetPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DeletePresetPayload::index,
			ByteBufCodecs.STRING_UTF8, DeletePresetPayload::name,
			DeletePresetPayload::new).cast();

	@Override
	public Type<DeletePresetPayload> type() {
		return TYPE;
	}
}
