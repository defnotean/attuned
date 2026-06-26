package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.pacts.PactTrialProgress;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client mirror of the player-owned Attuned attachments used by HUDs and client controls. */
public record AttunementStatePayload(
		int capacity,
		AttunedInv inventory,
		List<FocusPreset> presets,
		float resonance,
		PactTrialProgress pactTrialProgress,
		List<String> discoveredConfluences) implements CustomPacketPayload {
	public AttunementStatePayload {
		inventory = inventory == null ? AttunedInv.empty() : inventory;
		presets = presets == null ? List.of() : List.copyOf(presets);
		if (!Float.isFinite(resonance)) {
			resonance = 0.0F;
		}
		resonance = Math.min(1.0F, Math.max(0.0F, resonance));
		pactTrialProgress = pactTrialProgress == null ? PactTrialProgress.EMPTY : pactTrialProgress;
		discoveredConfluences = discoveredConfluences == null ? List.of() : List.copyOf(discoveredConfluences);
	}

	public static final Type<AttunementStatePayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "attunement_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AttunementStatePayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AttunementStatePayload::capacity,
			AttunedInv.STREAM_CODEC, AttunementStatePayload::inventory,
			FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list()), AttunementStatePayload::presets,
			ByteBufCodecs.FLOAT, AttunementStatePayload::resonance,
			PactTrialProgress.STREAM_CODEC, AttunementStatePayload::pactTrialProgress,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttunementStatePayload::discoveredConfluences,
			AttunementStatePayload::new).cast();

	@Override
	public Type<AttunementStatePayload> type() {
		return TYPE;
	}
}
