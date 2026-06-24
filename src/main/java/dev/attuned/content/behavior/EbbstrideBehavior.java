package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Ebbstride fallback for branches without the vanilla fall-damage-multiplier attribute. */
public final class EbbstrideBehavior implements FocusBehavior {
	private static final ResourceLocation FOCUS_ID =
		new ResourceLocation(Attuned.MOD_ID, "ebbstride_focus");
	private static final float FALL_DAMAGE_SCALE = 0.6F;

	public static float adjustFallDamage(ServerPlayer player, float amount) {
		return hasActiveFocus(player) ? amount * FALL_DAMAGE_SCALE : amount;
	}

	private static boolean hasActiveFocus(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inv.get(slot);
			if (!stack.isEmpty() && FOCUS_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				return true;
			}
		}
		return false;
	}
}
