package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class PacketType<T extends FabricPacket> {
	private static final Map<ResourceLocation, PacketType<?>> TYPES = new HashMap<>();

	private final ResourceLocation id;
	private final Function<FriendlyByteBuf, T> reader;

	private PacketType(ResourceLocation id, Function<FriendlyByteBuf, T> reader) {
		this.id = id;
		this.reader = reader;
		TYPES.put(id, this);
	}

	public static <T extends FabricPacket> PacketType<T> create(ResourceLocation id, Function<FriendlyByteBuf, T> reader) {
		return new PacketType<>(id, reader);
	}

	static PacketType<?> byId(ResourceLocation id) {
		return TYPES.get(id);
	}

	public ResourceLocation getId() {
		return id;
	}

	FabricPacket read(FriendlyByteBuf buf) {
		return reader.apply(buf);
	}
}
