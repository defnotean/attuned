package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedInv;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client state for the player's selected Focus Ability and cooldown. */
public record FocusAbilityStatusPayload(int slot, int remainingTicks, int totalTicks) implements CustomPacketPayload {
	public static final int NO_ABILITY_SLOT = -1;
	public static final int PACT_TACTICAL_SLOT = -2;

	public FocusAbilityStatusPayload {
		if (slot == PACT_TACTICAL_SLOT) {
			totalTicks = Math.max(0, totalTicks);
			remainingTicks = Math.min(Math.max(0, remainingTicks),
				totalTicks > 0 ? totalTicks : Math.max(0, remainingTicks));
		} else if (slot < 0 || slot >= AttunedInv.SIZE) {
			slot = NO_ABILITY_SLOT;
			remainingTicks = 0;
			totalTicks = 0;
		} else {
			totalTicks = Math.max(0, totalTicks);
			remainingTicks = Math.min(Math.max(0, remainingTicks), totalTicks);
		}
	}

	public static final Type<FocusAbilityStatusPayload> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "focus_ability_status"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FocusAbilityStatusPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FocusAbilityStatusPayload::slot,
			ByteBufCodecs.VAR_INT, FocusAbilityStatusPayload::remainingTicks,
			ByteBufCodecs.VAR_INT, FocusAbilityStatusPayload::totalTicks,
			FocusAbilityStatusPayload::new).cast();

	@Override
	public Type<FocusAbilityStatusPayload> type() {
		return TYPE;
	}
}
