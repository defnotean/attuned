package dev.attuned.content.behavior;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Rootstep Focus: modest movement and safer footing while standing on natural blocks. */
public final class RootstepBehavior implements FocusBehavior {
	private static final ResourceLocation SPEED_ID =
		new ResourceLocation(Attuned.MOD_ID, "rootstep_focus_speed");
	private static final double MOVEMENT_SPEED = 0.05D;
	// Minecraft 1.19.x has no vanilla FALL_DAMAGE_MULTIPLIER attribute.

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (onNaturalFooting(player)) {
			apply(player);
		} else {
			remove(player);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		remove(player);
	}

	private static boolean onNaturalFooting(ServerPlayer player) {
		BlockState state = player.getLevel().getBlockState(player.blockPosition().below());
		return state.is(BlockTags.LEAVES)
			|| state.is(BlockTags.LOGS)
			|| state.is(BlockTags.DIRT)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.MOSS_CARPET)
			|| state.is(Blocks.CLAY);
	}

	private static void apply(ServerPlayer player) {
		applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED),
			SPEED_ID, MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE);
	}

	private static void applyModifier(AttributeInstance attribute, ResourceLocation id, double amount,
			AttributeModifier.Operation operation) {
		if (attribute == null || attribute.getModifier(AttributeModifierIds.uuid(id)) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(id), AttributeModifierIds.name(id), amount, operation));
	}

	private static void remove(ServerPlayer player) {
		removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID);
	}

	private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
		if (attribute != null) {
			attribute.removeModifier(AttributeModifierIds.uuid(id));
		}
	}
}
