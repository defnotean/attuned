package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server signal that the player pressed the Altar GUI's "Bind"
 * button. It carries no data; the server validates that the player has the
 * Altar menu open and a shard in the input slot before consuming one shard
 * and raising attunement capacity, so a forged payload only ever fires what
 * the player legitimately could.
 */
public record BindShardPayload() implements CustomPacketPayload {

	public static final Type<BindShardPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "bind_shard"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BindShardPayload> CODEC =
		StreamCodec.unit(new BindShardPayload());

	@Override
	public Type<BindShardPayload> type() {
		return TYPE;
	}
}
