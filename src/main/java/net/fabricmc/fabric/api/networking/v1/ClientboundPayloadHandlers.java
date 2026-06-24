package net.fabricmc.fabric.api.networking.v1;

import java.util.function.Consumer;

public final class ClientboundPayloadHandlers {
	private static Consumer<FabricPacket> dispatcher;

	private ClientboundPayloadHandlers() {}

	public static void setDispatcher(Consumer<FabricPacket> dispatcher) {
		ClientboundPayloadHandlers.dispatcher = dispatcher;
	}

	static void dispatch(FabricPacket payload) {
		if (dispatcher != null) {
			dispatcher.accept(payload);
		}
	}
}
