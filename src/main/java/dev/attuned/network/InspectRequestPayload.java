package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server request to inspect another player's public attunement state.
 *
 * <p>The payload carries only the crosshair-picked target's entity id. The server
 * resolves the entity, re-validates range and line of sight from the requester,
 * rate-limits the requester, and replies on the requester's action bar — so a
 * forged id only ever reveals the same Discord/committed/Apex state any onlooker
 * can already infer from the Combat HUD's enemy gem.</p>
 */
public record InspectRequestPayload(int targetEntityId) implements CustomPacketPayload {

	public static final Type<InspectRequestPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "inspect_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, InspectRequestPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, InspectRequestPayload::targetEntityId,
			InspectRequestPayload::new).cast();

	@Override
	public Type<InspectRequestPayload> type() {
		return TYPE;
	}
}
