package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeletePresetPayload(int index) implements CustomPacketPayload {
	public DeletePresetPayload {
		if (index < 0) {
			index = -1;
		}
	}

	public static final Type<DeletePresetPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "delete_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeletePresetPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, DeletePresetPayload::index, DeletePresetPayload::new).cast();

	@Override
	public Type<DeletePresetPayload> type() {
		return TYPE;
	}
}
