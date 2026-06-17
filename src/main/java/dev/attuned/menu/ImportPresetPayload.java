package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImportPresetPayload(String name, List<String> slots) implements CustomPacketPayload {
	public ImportPresetPayload {
		FocusPreset normalized = new FocusPreset(name, slots);
		name = normalized.name();
		slots = normalized.slots();
	}

	public static final Type<ImportPresetPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "import_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ImportPresetPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ImportPresetPayload::name,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ImportPresetPayload::slots,
			ImportPresetPayload::new).cast();

	@Override
	public Type<ImportPresetPayload> type() {
		return TYPE;
	}
}
