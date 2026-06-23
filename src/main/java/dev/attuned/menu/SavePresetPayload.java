package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusPreset;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SavePresetPayload(String name) implements FabricPacket {
	public SavePresetPayload {
		name = FocusPreset.sanitizeName(name);
	}

	public static final PacketType<SavePresetPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "save_preset"), SavePresetPayload::new);

	public SavePresetPayload(FriendlyByteBuf buf) {
		this(buf.readUtf(32));
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
