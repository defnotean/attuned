package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPlayNetworking {
	private static final Map<CustomPacketPayload.Type<?>, Receiver<?>> RECEIVERS = new HashMap<>();

	private ServerPlayNetworking() {}

	public static <T extends CustomPacketPayload> void registerGlobalReceiver(
			CustomPacketPayload.Type<T> type, Receiver<T> receiver) {
		RECEIVERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(receiver, "receiver"));
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	static <T extends CustomPacketPayload> void receive(T payload, IPayloadContext neoContext) {
		@SuppressWarnings("unchecked")
		Receiver<T> receiver = (Receiver<T>) RECEIVERS.get(payload.type());
		if (receiver == null) {
			return;
		}
		if (!(neoContext.player() instanceof ServerPlayer player)) {
			return;
		}
		receiver.receive(payload, new Context(player));
	}

	@FunctionalInterface
	public interface Receiver<T extends CustomPacketPayload> {
		void receive(T payload, Context context);
	}

	public record Context(ServerPlayer player) {
	}
}
