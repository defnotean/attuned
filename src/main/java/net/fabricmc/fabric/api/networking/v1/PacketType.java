package net.fabricmc.fabric.api.networking.v1;

import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 1.18 compatibility id plus decoder for FabricPacket-style records. */
public class PacketType<T extends FabricPacket> extends ResourceLocation {
	private final Function<FriendlyByteBuf, T> reader;

	private PacketType(ResourceLocation id, Function<FriendlyByteBuf, T> reader) {
		super(id.getNamespace(), id.getPath());
		this.reader = reader;
	}

	public static <T extends FabricPacket> PacketType<T> create(ResourceLocation id,
			Function<FriendlyByteBuf, T> reader) {
		return new PacketType<>(id, reader);
	}

	public T read(FriendlyByteBuf buf) {
		return reader.apply(buf);
	}
}
