package dev.attuned.content.behavior;

import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.behavior.DataFocusBehaviors.UseItemWindowBehavior;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Routes datapack {@code attuned:use_item_window} behaviors from the server item-use event. */
public final class PaletteItemUse {
	private static boolean initialized;

	private PaletteItemUse() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		UseItemCallback.EVENT.register(PaletteItemUse::useItem);
	}

	static InteractionResultHolder<ItemStack> useItem(Player player, Level level, InteractionHand hand) {
		ItemStack used = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(used);
		}
		if (used.isEmpty()) {
			return InteractionResultHolder.pass(used);
		}
		AttunedInv inventory = AttunedAttachments.getInventory(serverPlayer);
		for (int slot : Attunement.activeSlots(serverPlayer)) {
			ItemStack focus = inventory.get(slot);
			if (focus.isEmpty()) {
				continue;
			}
			Attunement.definitionFor(serverPlayer, focus)
				.flatMap(FocusDefinition::behavior)
				.ifPresent(behaviorId -> {
					FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId, serverPlayer.registryAccess());
					if (behavior instanceof UseItemWindowBehavior useItem) {
						useItem.tryUse(serverPlayer, used);
					}
				});
		}
		return InteractionResultHolder.pass(used);
	}
}
