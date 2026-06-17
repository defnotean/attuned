package dev.attuned.network;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server Updraft Focus flight control state. */
public record UpdraftLiftPayload(boolean boosting, boolean braking) implements FabricPacket {
	public static final PacketType<UpdraftLiftPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "updraft_lift"), UpdraftLiftPayload::new);

	public UpdraftLiftPayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), buf.readBoolean());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(boosting);
		buf.writeBoolean(braking);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
