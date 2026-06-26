package net.fabricmc.fabric.api.networking.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PayloadTypeRegistry {
	private static final PlayRegistry SERVERBOUND_PLAY = new PlayRegistry(PacketFlow.SERVERBOUND);
	private static final PlayRegistry CLIENTBOUND_PLAY = new PlayRegistry(PacketFlow.CLIENTBOUND);
	private static final List<Registration<?>> REGISTRATIONS = new ArrayList<>();
	private static IEventBus modEventBus;
	private static boolean listenerRegistered;

	private PayloadTypeRegistry() {}

	public static synchronized void setModEventBus(IEventBus eventBus) {
		modEventBus = Objects.requireNonNull(eventBus, "eventBus");
		if (!listenerRegistered) {
			modEventBus.addListener(PayloadTypeRegistry::registerPayloads);
			listenerRegistered = true;
		}
	}

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
		if (!listenerRegistered) {
			throw new IllegalStateException("NeoForge mod event bus must be set before registering payloads");
		}
	}

	private static synchronized void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1").optional();
		for (Registration<?> registration : List.copyOf(REGISTRATIONS)) {
			registration.register(registrar);
		}
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

	private static synchronized <T extends CustomPacketPayload> void registerPayload(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketFlow flow) {
		@SuppressWarnings("unchecked")
		StreamCodec<RegistryFriendlyByteBuf, T> registryCodec =
			(StreamCodec<RegistryFriendlyByteBuf, T>) codec;
		REGISTRATIONS.add(new Registration<>(type, registryCodec, flow));
	}

	private record Registration<T extends CustomPacketPayload>(
			CustomPacketPayload.Type<T> type,
			StreamCodec<RegistryFriendlyByteBuf, T> codec,
			PacketFlow flow) {
		void register(PayloadRegistrar registrar) {
			if (flow == PacketFlow.SERVERBOUND) {
				registrar.playToServer(type, codec, (payload, context) ->
					ServerPlayNetworking.receive(payload, (IPayloadContext) context));
			} else {
				registrar.playToClient(type, codec, (payload, context) ->
					ClientboundPayloadHandlers.dispatch(payload, (IPayloadContext) context));
			}
		}
	}
}
