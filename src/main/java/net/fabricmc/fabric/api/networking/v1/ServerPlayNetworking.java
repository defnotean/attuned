package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ServerPlayNetworking {
	private static final Map<PacketType<?>, Receiver<?>> RECEIVERS = new HashMap<>();

	private ServerPlayNetworking() {}

	public static <T extends FabricPacket> void registerGlobalReceiver(PacketType<T> type, Receiver<T> receiver) {
		RECEIVERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(receiver, "receiver"));
	}

	public static void send(ServerPlayer player, FabricPacket payload) {
		FabricNetworkingChannel.sendTo(player, payload);
	}

	static <T extends FabricPacket> void receive(T payload, NetworkEvent.Context context) {
		@SuppressWarnings("unchecked")
		Receiver<T> receiver = (Receiver<T>) RECEIVERS.get(payload.getType());
		if (receiver == null) {
			return;
		}
		ServerPlayer player = context.getSender();
		if (player == null) {
			return;
		}
		receiver.receive(payload, player, responseSender -> {});
	}

	@FunctionalInterface
	public interface Receiver<T extends FabricPacket> {
		void receive(T payload, ServerPlayer player, ResponseSender sender);
	}

	@FunctionalInterface
	public interface ResponseSender {
		void sendPacket(FabricPacket payload);
	}
}
