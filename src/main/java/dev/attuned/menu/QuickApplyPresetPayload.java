package dev.attuned.menu;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record QuickApplyPresetPayload(int index) implements FabricPacket {
	public QuickApplyPresetPayload {
		if (index < 0) {
			index = -1;
		}
	}

	public static final PacketType<QuickApplyPresetPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "quick_apply_preset"),
			QuickApplyPresetPayload::new);

	public QuickApplyPresetPayload(FriendlyByteBuf buf) {
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
