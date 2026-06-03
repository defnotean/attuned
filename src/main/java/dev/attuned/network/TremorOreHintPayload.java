package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client signal telling Tremor which ore block to outline. */
public record TremorOreHintPayload(BlockPos orePos) implements CustomPacketPayload {
	public static final Type<TremorOreHintPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "tremor_ore_hint"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TremorOreHintPayload> CODEC =
		BlockPos.STREAM_CODEC.map(TremorOreHintPayload::new, TremorOreHintPayload::orePos).cast();

	@Override
	public Type<TremorOreHintPayload> type() {
		return TYPE;
	}
}
