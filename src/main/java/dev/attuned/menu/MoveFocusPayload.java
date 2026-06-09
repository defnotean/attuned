package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.content.AttunedComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MoveFocusPayload(int direction, int satchelSlot, int equippedSlot) implements CustomPacketPayload {
	public MoveFocusPayload {
		if (direction != 0 && direction != 1) {
			direction = -1;
		}
		if (satchelSlot < 0 || satchelSlot >= AttunedComponents.SATCHEL_SIZE) {
			satchelSlot = -1;
		}
		if (equippedSlot < 0 || equippedSlot >= AttunedInv.SIZE) {
			equippedSlot = -1;
		}
	}

	public static final Type<MoveFocusPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "move_focus"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoveFocusPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MoveFocusPayload::direction,
			ByteBufCodecs.VAR_INT, MoveFocusPayload::satchelSlot,
			ByteBufCodecs.VAR_INT, MoveFocusPayload::equippedSlot,
			MoveFocusPayload::new).cast();

	@Override
	public Type<MoveFocusPayload> type() {
		return TYPE;
	}
}
