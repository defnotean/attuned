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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Rivet Focus: grounded knockback resistance while braced or standing on metal. */
public final class RivetBehavior implements FocusBehavior {
	private static final ResourceLocation BRACED_ID =
		new ResourceLocation(Attuned.MOD_ID, "rivet_focus_braced");
	private static final double KNOCKBACK_RESISTANCE = 0.15D;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (player.isOnGround() && (player.isCrouching() || player.isBlocking() || onMetalFooting(player))) {
			apply(player);
		} else {
			remove(player);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		remove(player);
	}

	private static boolean onMetalFooting(ServerPlayer player) {
		BlockState state = player.getLevel().getBlockState(player.blockPosition().below());
		return state.is(Blocks.IRON_BLOCK)
			|| state.is(Blocks.RAW_IRON_BLOCK)
			|| state.is(Blocks.GOLD_BLOCK)
			|| state.is(Blocks.RAW_GOLD_BLOCK)
			|| state.is(Blocks.COPPER_BLOCK)
			|| state.is(Blocks.RAW_COPPER_BLOCK)
			|| state.is(Blocks.NETHERITE_BLOCK)
			|| state.is(Blocks.ANVIL)
			|| state.is(Blocks.CHIPPED_ANVIL)
			|| state.is(Blocks.DAMAGED_ANVIL);
	}

	private static void apply(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute == null || attribute.getModifier(AttributeModifierIds.uuid(BRACED_ID)) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(BRACED_ID), AttributeModifierIds.name(BRACED_ID), KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION));
	}

	private static void remove(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute != null) {
			attribute.removeModifier(AttributeModifierIds.uuid(BRACED_ID));
		}
	}
}
