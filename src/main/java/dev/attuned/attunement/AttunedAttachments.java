package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import dev.attuned.Attuned;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player attunement state: the attunement capacity and the six Focus slots.
 * Both are persistent across restarts and synced to the owning client.
 */
public final class AttunedAttachments {
	private AttunedAttachments() {}

	public static final AttachmentType<Integer> CAPACITY = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "capacity"),
		builder -> builder
			.initializer(() -> 0)
			.persistent(Codec.INT)
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	public static final AttachmentType<AttunedInv> INVENTORY = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "inventory"),
		builder -> builder
			.initializer(AttunedInv::empty)
			.persistent(AttunedInv.CODEC)
			.syncWith(AttunedInv.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Forces this class to load so the attachment types register during mod init. */
	public static void init() {}

	public static int getCapacity(Player player) {
		return player.getAttachedOrElse(CAPACITY, 0);
	}

	public static void setCapacity(Player player, int value) {
		player.setAttached(CAPACITY, Math.max(0, value));
	}

	public static AttunedInv getInventory(Player player) {
		return player.getAttachedOrElse(INVENTORY, AttunedInv.empty());
	}

	public static void setSlot(Player player, int slot, ItemStack stack) {
		// Read through getInventory (never null — falls back to an empty inventory),
		// then replace the whole value. Do not use modifyAttached here: it passes
		// null to its operator when the attachment has never been set.
		player.setAttached(INVENTORY, getInventory(player).with(slot, stack));
	}
}
