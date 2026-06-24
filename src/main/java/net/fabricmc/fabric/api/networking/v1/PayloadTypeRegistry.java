package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuildable;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadConnection;
import net.minecraftforge.network.payload.PayloadFlow;

public final class PayloadTypeRegistry {
	private static final PlayRegistry SERVERBOUND_PLAY = new PlayRegistry(PacketFlow.SERVERBOUND);
	private static final PlayRegistry CLIENTBOUND_PLAY = new PlayRegistry(PacketFlow.CLIENTBOUND);
	private static final PayloadConnection<CustomPacketPayload> BUILDER =
		ChannelBuilder.named(Identifier.fromNamespaceAndPath("attuned", "play"))
			.networkProtocolVersion(1)
			.optional()
			.payloadChannel();
	private static ChannelBuildable<CustomPacketPayload> buildable;
	private static Channel<CustomPacketPayload> channel;

	private PayloadTypeRegistry() {}

	public static PlayRegistry serverboundPlay() {
		return SERVERBOUND_PLAY;
	}

	public static PlayRegistry playC2S() {
		return serverboundPlay();
	}

	public static PlayRegistry clientboundPlay() {
		return CLIENTBOUND_PLAY;
	}

	public static PlayRegistry playS2C() {
		return clientboundPlay();
	}

	public static synchronized void buildForgeChannel() {
		if (channel != null) {
			return;
		}
		if (buildable == null) {
			throw new IllegalStateException("Attuned network channel has no registered payloads");
		}
		channel = buildable.build();
	}

	public static synchronized Channel<CustomPacketPayload> channel() {
		if (channel == null) {
			buildForgeChannel();
		}
		return channel;
	}

	public static final class PlayRegistry {
		private final PacketFlow flow;

		private PlayRegistry(PacketFlow flow) {
			this.flow = flow;
		}

		public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
				StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
			registerPayload(type, codec, flow);
		}
	}

	private static <T extends CustomPacketPayload> void registerPayload(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketFlow flow) {
		@SuppressWarnings("unchecked")
		StreamCodec<RegistryFriendlyByteBuf, T> registryCodec =
			(StreamCodec<RegistryFriendlyByteBuf, T>) codec;
		PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> payloadFlow = BUILDER.play().flow(flow);
		payloadFlow.addMain(type, registryCodec, (payload, context) -> {
			if (flow == PacketFlow.SERVERBOUND) {
				ServerPlayNetworking.receive(payload, context);
			} else {
				ClientboundPayloadHandlers.dispatch(payload, context);
			}
		});
		buildable = payloadFlow;
	}
}
