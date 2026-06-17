package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Branch-local replacement for Fabric attachment sync on Minecraft 1.19.4. */
public record AttunedStatePayload(CompoundTag tag) implements FabricPacket {
	public AttunedStatePayload {
		tag = tag == null ? new CompoundTag() : tag.copy();
	}

	public static final PacketType<AttunedStatePayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "attuned_state"), AttunedStatePayload::new);

	public AttunedStatePayload(FriendlyByteBuf buf) {
		this(buf.readNbt());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeNbt(tag);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
