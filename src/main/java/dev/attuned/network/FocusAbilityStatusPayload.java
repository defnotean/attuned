package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedInv;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client state for the player's selected Focus Ability and cooldown. */
public record FocusAbilityStatusPayload(int slot, int remainingTicks, int totalTicks) implements FabricPacket {
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

	public static final PacketType<FocusAbilityStatusPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "focus_ability_status"),
			FocusAbilityStatusPayload::new);

	public FocusAbilityStatusPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(slot);
		buf.writeVarInt(remainingTicks);
		buf.writeVarInt(totalTicks);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
