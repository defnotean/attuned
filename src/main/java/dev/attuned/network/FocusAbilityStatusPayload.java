package dev.attuned.network;

import dev.attuned.Attuned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client state for the player's selected Focus Ability and cooldown. */
public record FocusAbilityStatusPayload(int slot, int remainingTicks, int totalTicks) implements CustomPacketPayload {
	public static final int NO_ABILITY_SLOT = -1;

	public static final Type<FocusAbilityStatusPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "focus_ability_status"));

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
