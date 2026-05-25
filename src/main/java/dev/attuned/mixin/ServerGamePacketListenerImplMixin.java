package dev.attuned.mixin;

import dev.attuned.menu.FocusSlot;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the creative inventory edit our Focus slots.
 *
 * <p>Creative-mode slot edits sync to the server with {@link
 * ServerboundSetCreativeModeSlotPacket}, and the vanilla handler only accepts
 * the standard inventory indices (1-45). A Focus slot lives at a higher menu
 * index, so without this its creative-mode edits would be dropped on the server
 * and the item lost. We intercept packets aimed at a Focus slot and apply them
 * straight to that slot, which writes through to the player's attunement
 * attachment exactly as a survival-mode click would.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	/**
	 * Injected after the packet has been bounced onto the server thread, so the
	 * slot write happens on the same thread vanilla would use.
	 */
	@Inject(
		method = "handleSetCreativeModeSlot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void attuned$routeFocusSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		int slotNum = packet.slotNum();
		AbstractContainerMenu menu = this.player.inventoryMenu;
		if (slotNum < 0 || slotNum >= menu.slots.size()
				|| !(menu.getSlot(slotNum) instanceof FocusSlot)) {
			// Not one of our slots — let vanilla handle it.
			return;
		}
		FocusSlot slot = (FocusSlot) menu.getSlot(slotNum);
		ci.cancel();
		if (!this.player.hasInfiniteMaterials()) {
			return;
		}
		ItemStack stack = packet.itemStack();
		if (!stack.isItemEnabled(this.player.level().enabledFeatures())) {
			return;
		}
		if (!stack.isEmpty() && !slot.mayPlace(stack)) {
			return;
		}
		if (stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize()) {
			// Mirrors vanilla's own slot-1-to-45 path: write the slot, keep the
			// remote-slot mirror in step, and flush.
			slot.setByPlayer(stack);
			menu.setRemoteSlot(slotNum, stack);
			menu.broadcastChanges();
		}
	}
}
