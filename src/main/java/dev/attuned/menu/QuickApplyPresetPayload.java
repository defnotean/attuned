package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Hotkey-driven preset apply. Unlike {@link ApplyPresetPayload} this does not
 * require the reliquary screen to be open: the server sources Foci from the
 * first Focus Reliquary found in the player's inventory (plus equipped and
 * loose inventory Foci), so a bound build key works mid-gameplay.
 */
public record QuickApplyPresetPayload(int index) implements CustomPacketPayload {
	public QuickApplyPresetPayload {
		if (index < 0) {
			index = -1;
		}
	}

	public static final Type<QuickApplyPresetPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "quick_apply_preset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuickApplyPresetPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, QuickApplyPresetPayload::index, QuickApplyPresetPayload::new).cast();

	@Override
	public Type<QuickApplyPresetPayload> type() {
		return TYPE;
	}
}
