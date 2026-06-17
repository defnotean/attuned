package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Anchor Focus: grants knockback resistance only while the wearer is bracing.
 */
public final class AnchorBehavior implements FocusBehavior {
	private static final ResourceLocation BRACED_ID =
		ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "anchor_focus_braced");
	private static final double KNOCKBACK_RESISTANCE = 0.45;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (player.isCrouching() || player.isBlocking()) {
			apply(player);
		} else {
			remove(player);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		remove(player);
	}

	private static void apply(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute == null || attribute.getModifier(BRACED_ID) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(
			BRACED_ID, KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void remove(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute != null) {
			attribute.removeModifier(BRACED_ID);
		}
	}
}
