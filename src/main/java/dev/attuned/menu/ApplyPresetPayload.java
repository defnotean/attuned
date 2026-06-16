package dev.attuned.menu;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ApplyPresetPayload(int index) implements FabricPacket {
	public ApplyPresetPayload {
		if (index < 0) {
			index = -1;
		}
	}

	public static final PacketType<ApplyPresetPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "apply_preset"), ApplyPresetPayload::new);

	public ApplyPresetPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(index);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
