package net.fabricmc.fabric.api.networking.v1;

import java.util.function.Consumer;

public final class ClientboundPayloadHandlers {
	private static Consumer<FabricPayloadMessage> dispatcher;

	private ClientboundPayloadHandlers() {}

	public static void setDispatcher(Consumer<FabricPayloadMessage> dispatcher) {
		ClientboundPayloadHandlers.dispatcher = dispatcher;
	}

	static void dispatch(FabricPayloadMessage payload) {
		if (dispatcher != null) {
			dispatcher.accept(payload);
		}
	}
}
