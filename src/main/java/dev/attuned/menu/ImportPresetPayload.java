package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ImportPresetPayload(FocusPreset preset) implements FabricPacket {
	public ImportPresetPayload(String name, List<String> slots) {
		this(new FocusPreset(name, slots));
	}

	public ImportPresetPayload {
		preset = preset == null ? new FocusPreset("", List.of()) :
			new FocusPreset(preset.name(), preset.slots(), preset.metadata());
	}

	public static final PacketType<ImportPresetPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "import_preset"), ImportPresetPayload::new);

	public ImportPresetPayload(FriendlyByteBuf buf) {
		this(FocusPreset.read(buf));
	}

	public String name() {
		return preset.name();
	}

	public List<String> slots() {
		return preset.slots();
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		preset.write(buf);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
