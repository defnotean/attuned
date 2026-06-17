package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SavePresetPayload(String name) implements CustomPacketPayload {
	public SavePresetPayload {
		name = name == null ? "" : name.trim();
		if (name.length() > 32) {
			name = name.substring(0, 32);
		}
	}

	public static final Type<SavePresetPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "save_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SavePresetPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SavePresetPayload::name, SavePresetPayload::new).cast();

	@Override
	public Type<SavePresetPayload> type() {
		return TYPE;
	}
}
