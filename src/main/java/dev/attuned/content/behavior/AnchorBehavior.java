package dev.attuned.content.behavior;

import dev.attuned.compat.AttributeModifierIds;

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
		new ResourceLocation(Attuned.MOD_ID, "anchor_focus_braced");
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
		if (attribute == null || attribute.getModifier(AttributeModifierIds.uuid(BRACED_ID)) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(BRACED_ID), AttributeModifierIds.name(BRACED_ID), KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void remove(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute != null) {
			attribute.removeModifier(AttributeModifierIds.uuid(BRACED_ID));
		}
	}
}
