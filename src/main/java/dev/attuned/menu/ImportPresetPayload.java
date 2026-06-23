package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ImportPresetPayload(FocusPreset preset) implements CustomPacketPayload {
	public ImportPresetPayload(String name, List<String> slots) {
		this(new FocusPreset(name, slots));
	}

	public ImportPresetPayload {
		preset = preset == null ? new FocusPreset("", List.of()) :
			new FocusPreset(preset.name(), preset.slots(), preset.metadata());
	}

	public static final Type<ImportPresetPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "import_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ImportPresetPayload> CODEC =
		StreamCodec.composite(
			FocusPreset.STREAM_CODEC, ImportPresetPayload::preset,
			ImportPresetPayload::new).cast();

	public String name() {
		return preset.name();
	}

	public List<String> slots() {
		return preset.slots();
	}

	@Override
	public Type<ImportPresetPayload> type() {
		return TYPE;
	}
}
