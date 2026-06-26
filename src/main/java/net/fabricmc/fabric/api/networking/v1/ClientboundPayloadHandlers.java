package net.fabricmc.fabric.api.networking.v1;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientboundPayloadHandlers {
	private static BiConsumer<CustomPacketPayload, IPayloadContext> dispatcher = (payload, context) -> {};

	private ClientboundPayloadHandlers() {}

	public static void setDispatcher(BiConsumer<CustomPacketPayload, IPayloadContext> newDispatcher) {
		dispatcher = Objects.requireNonNull(newDispatcher, "newDispatcher");
	}

	static void dispatch(CustomPacketPayload payload, IPayloadContext context) {
		dispatcher.accept(payload, context);
	}
}
