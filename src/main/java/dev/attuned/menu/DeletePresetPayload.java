package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record DeletePresetPayload(int index, String name) implements FabricPacket {
	public DeletePresetPayload {
		if (index < 0) {
			index = -1;
		}
		name = FocusPreset.sanitizeName(name);
	}

	public static final PacketType<DeletePresetPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "delete_preset"), DeletePresetPayload::new);

	public DeletePresetPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readUtf(32));
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(index);
		buf.writeUtf(name);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
