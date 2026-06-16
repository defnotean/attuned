package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server signal that the player pressed the Focus Ability keybind. It
 * carries no data; the server resolves the one active ability Focus entirely
 * from the player's own active Foci.
 */
public record AbilityPayload() implements FabricPacket {
	public static final PacketType<AbilityPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "ability"), AbilityPayload::new);

	public AbilityPayload(FriendlyByteBuf buf) {
		this();
	}

	@Override
	public void write(FriendlyByteBuf buf) {}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
