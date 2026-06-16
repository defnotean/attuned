package dev.attuned.content.behavior;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Rainstep Focus: a modest movement boost while the wearer is in wet conditions.
 */
public final class RainstepBehavior implements FocusBehavior {
	private static final ResourceLocation WET_SPEED_ID =
		new ResourceLocation(Attuned.MOD_ID, "rainstep_focus_wet_speed");
	private static final double WET_SPEED = 0.12;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (isWet(player)) {
			apply(player);
		} else {
			remove(player);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		remove(player);
	}

	private static boolean isWet(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.getLevel();
		if (level.isRainingAt(player.blockPosition()) || player.isInWaterOrRain()) {
			return true;
		}
		BlockState state = level.getBlockState(player.blockPosition());
		return state.hasProperty(BlockStateProperties.WATERLOGGED)
			&& state.getValue(BlockStateProperties.WATERLOGGED);
	}

	private static void apply(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute == null || attribute.getModifier(AttributeModifierIds.uuid(WET_SPEED_ID)) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(WET_SPEED_ID), AttributeModifierIds.name(WET_SPEED_ID), WET_SPEED, AttributeModifier.Operation.MULTIPLY_BASE));
	}

	private static void remove(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute != null) {
			attribute.removeModifier(AttributeModifierIds.uuid(WET_SPEED_ID));
		}
	}
}
