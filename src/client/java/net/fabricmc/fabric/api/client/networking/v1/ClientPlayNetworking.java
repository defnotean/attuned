package net.fabricmc.fabric.api.client.networking.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPlayNetworking {
	private static final Map<CustomPacketPayload.Type<?>, Receiver<?>> RECEIVERS = new HashMap<>();

	static {
		net.fabricmc.fabric.api.networking.v1.ClientboundPayloadHandlers.setDispatcher(ClientPlayNetworking::receive);
	}

	private ClientPlayNetworking() {}

	public static <T extends CustomPacketPayload> void registerGlobalReceiver(
			CustomPacketPayload.Type<T> type, Receiver<T> receiver) {
		RECEIVERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(receiver, "receiver"));
	}

	public static void send(CustomPacketPayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}

	public static boolean canSend(CustomPacketPayload.Type<?> type) {
		return true;
	}

	private static <T extends CustomPacketPayload> void receive(T payload, IPayloadContext neoContext) {
		@SuppressWarnings("unchecked")
		Receiver<T> receiver = (Receiver<T>) RECEIVERS.get(payload.type());
		if (receiver != null) {
			receiver.receive(payload, new Context(Minecraft.getInstance()));
		}
	}

	@FunctionalInterface
	public interface Receiver<T extends CustomPacketPayload> {
		void receive(T payload, Context context);
	}

	public record Context(Minecraft client) {
	}
}
