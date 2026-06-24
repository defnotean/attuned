package net.fabricmc.fabric.api.client.networking.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.ClientboundPayloadHandlers;
import net.fabricmc.fabric.api.networking.v1.FabricNetworkingChannel;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class ClientPlayNetworking {
	private static final Map<PacketType<?>, Receiver<?>> RECEIVERS = new HashMap<>();

	static {
		ClientboundPayloadHandlers.setDispatcher(ClientPlayNetworking::receive);
	}

	private ClientPlayNetworking() {}

	public static <T extends FabricPacket> void registerGlobalReceiver(PacketType<T> type, Receiver<T> receiver) {
		RECEIVERS.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(receiver, "receiver"));
	}

	public static boolean canSend(PacketType<?> type) {
		return true;
	}

	public static void send(FabricPacket payload) {
		FabricNetworkingChannel.sendToServer(payload);
	}

	private static <T extends FabricPacket> void receive(T payload) {
		@SuppressWarnings("unchecked")
		Receiver<T> receiver = (Receiver<T>) RECEIVERS.get(payload.getType());
		if (receiver == null) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		receiver.receive(payload, player, responseSender -> {});
	}

	@FunctionalInterface
	public interface Receiver<T extends FabricPacket> {
		void receive(T payload, LocalPlayer player, ResponseSender sender);
	}

	@FunctionalInterface
	public interface ResponseSender {
		void sendPacket(FabricPacket payload);
	}
}
