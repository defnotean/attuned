package net.fabricmc.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ServerPlayNetworking {
	private static final Map<PacketType<?>, Receiver<?>> RECEIVERS = new HashMap<>();
	private static final Map<PacketType<?>, RawReceiver> RAW_RECEIVERS = new HashMap<>();

	private ServerPlayNetworking() {}

	public static <T extends FabricPacket> void registerGlobalReceiver(PacketType<T> type, Receiver<T> receiver) {
		RECEIVERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(receiver, "receiver"));
	}

	public static void registerGlobalReceiver(PacketType<?> type, RawReceiver receiver) {
		RAW_RECEIVERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(receiver, "receiver"));
	}

	public static void send(ServerPlayer player, FabricPacket payload) {
		FabricNetworkingChannel.sendTo(player, payload);
	}

	public static void send(ServerPlayer player, PacketType<?> type, FriendlyByteBuf payload) {
		FabricNetworkingChannel.sendTo(player, type, payload);
	}

	static void receive(FabricPayloadMessage message, NetworkEvent.Context context) {
		FabricPacket payload = message.payload();
		@SuppressWarnings({"rawtypes", "unchecked"})
		Receiver receiver = RECEIVERS.get(payload.getType());
		ServerPlayer player = context.getSender();
		if (player == null) {
			return;
		}
		if (receiver != null) {
			receiver.receive(payload, player, responseSender -> {});
			return;
		}
		RawReceiver rawReceiver = RAW_RECEIVERS.get(message.type());
		if (rawReceiver != null) {
			rawReceiver.receive(player.getLevel().getServer(), player, null, message.rawBuffer(), responseSender -> {});
		}
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

	@FunctionalInterface
	public interface RawReceiver {
		void receive(MinecraftServer server, ServerPlayer player, Object handler, FriendlyByteBuf buf, ResponseSender sender);
	}
}
