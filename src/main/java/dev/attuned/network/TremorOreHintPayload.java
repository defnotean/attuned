package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client signal telling Tremor which ore vein blocks to outline. */
public record TremorOreHintPayload(List<BlockPos> orePositions) implements FabricPacket {
	public static final PacketType<TremorOreHintPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "tremor_ore_hint"), TremorOreHintPayload::new);

	public TremorOreHintPayload {
		orePositions = List.copyOf(orePositions);
	}

	public TremorOreHintPayload(FriendlyByteBuf buf) {
		this(buf.readList(FriendlyByteBuf::readBlockPos));
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeCollection(orePositions, FriendlyByteBuf::writeBlockPos);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
