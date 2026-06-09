package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.Identifier;
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
	private static final Identifier SPEED_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "rootstep_focus_speed");
	private static final Identifier FALL_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "rootstep_focus_fall");
	private static final double MOVEMENT_SPEED = 0.05D;
	private static final double FALL_DAMAGE_REDUCTION = -0.15D;

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
		BlockState state = player.level().getBlockState(player.blockPosition().below());
		return state.is(BlockTags.LEAVES)
			|| state.is(BlockTags.LOGS)
			|| state.is(BlockTags.DIRT)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.MOSS_CARPET)
			|| state.is(Blocks.MUD)
			|| state.is(Blocks.CLAY);
	}

	private static void apply(ServerPlayer player) {
		applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED),
			SPEED_ID, MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		applyModifier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER),
			FALL_ID, FALL_DAMAGE_REDUCTION, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void applyModifier(AttributeInstance attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		if (attribute == null || attribute.getModifier(id) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
	}

	private static void remove(ServerPlayer player) {
		removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID);
		removeModifier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_ID);
	}

	private static void removeModifier(AttributeInstance attribute, Identifier id) {
		if (attribute != null) {
			attribute.removeModifier(id);
		}
	}
}
