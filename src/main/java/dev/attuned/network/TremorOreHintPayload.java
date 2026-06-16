package dev.attuned.network;

import dev.attuned.Attuned;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client signal telling Tremor which ore vein blocks to outline. */
public record TremorOreHintPayload(List<BlockPos> orePositions) implements CustomPacketPayload {
	public static final Type<TremorOreHintPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "tremor_ore_hint"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TremorOreHintPayload> CODEC =
		BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())
			.map(TremorOreHintPayload::new, TremorOreHintPayload::orePositions).cast();

	public TremorOreHintPayload {
		orePositions = List.copyOf(orePositions);
	}

	@Override
	public Type<TremorOreHintPayload> type() {
		return TYPE;
	}
}
