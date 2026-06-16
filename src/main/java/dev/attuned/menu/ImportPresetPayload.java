package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ImportPresetPayload(String name, List<String> slots) implements FabricPacket {
	public ImportPresetPayload {
		FocusPreset normalized = new FocusPreset(name, slots);
		name = normalized.name();
		slots = normalized.slots();
	}

	public static final PacketType<ImportPresetPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "import_preset"), ImportPresetPayload::new);

	public ImportPresetPayload(FriendlyByteBuf buf) {
		this(buf.readUtf(32), buf.readList(FriendlyByteBuf::readUtf));
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
		buf.writeCollection(slots, FriendlyByteBuf::writeUtf);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
