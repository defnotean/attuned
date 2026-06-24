package net.fabricmc.fabric.api.networking.v1;

import dev.attuned.Attuned;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FabricNetworkingChannel {
	private static final String PROTOCOL = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		new ResourceLocation(Attuned.MOD_ID, "play"),
		() -> PROTOCOL,
		PROTOCOL::equals,
		PROTOCOL::equals);

	static {
		CHANNEL.messageBuilder(FabricPayloadMessage.class, 0)
			.encoder(FabricPayloadMessage::encode)
			.decoder(FabricPayloadMessage::decode)
			.consumerMainThread(FabricNetworkingChannel::handle)
			.add();
	}

	private FabricNetworkingChannel() {}

	static void sendTo(ServerPlayer player, FabricPacket payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FabricPayloadMessage(payload));
	}

	static void sendTo(ServerPlayer player, PacketType<?> type, net.minecraft.network.FriendlyByteBuf payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FabricPayloadMessage(type, payload));
	}

	public static void sendToServer(FabricPacket payload) {
		CHANNEL.sendToServer(new FabricPayloadMessage(payload));
	}

	public static void sendToServer(PacketType<?> type, net.minecraft.network.FriendlyByteBuf payload) {
		CHANNEL.sendToServer(new FabricPayloadMessage(type, payload));
	}

	private static void handle(FabricPayloadMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		if (context.getDirection().getReceptionSide().isServer()) {
			ServerPlayNetworking.receive(message, context);
		} else {
			ClientboundPayloadHandlers.dispatch(message);
		}
		context.setPacketHandled(true);
	}
}
