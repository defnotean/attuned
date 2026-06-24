package net.fabricmc.fabric.api.networking.v1;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.event.network.CustomPayloadEvent;

public final class ClientboundPayloadHandlers {
	private static BiConsumer<CustomPacketPayload, CustomPayloadEvent.Context> dispatcher = (payload, context) -> {};

	private ClientboundPayloadHandlers() {}

	public static void setDispatcher(BiConsumer<CustomPacketPayload, CustomPayloadEvent.Context> newDispatcher) {
		dispatcher = Objects.requireNonNull(newDispatcher, "newDispatcher");
	}

	static void dispatch(CustomPacketPayload payload, CustomPayloadEvent.Context context) {
		dispatcher.accept(payload, context);
	}
}
