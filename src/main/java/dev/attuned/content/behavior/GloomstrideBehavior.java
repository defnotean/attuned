package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusCondition;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Gloomstride Focus (Umbral Eclipse): a modest movement-speed boost while the wearer
 * stands in low light. Mirrors {@link RainstepBehavior}'s transient-attribute toggle —
 * the modifier is added once the dark holds and removed the moment it lifts, under a
 * stable id so toggling stays idempotent. {@code onDeactivate} strips it unconditionally
 * so unequipping in darkness never strands the bonus.
 */
public final class GloomstrideBehavior implements FocusBehavior {
	private static final Identifier GLOOM_SPEED_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "gloomstride_focus_dark_speed");
	private static final double GLOOM_SPEED = 0.15;
	/** Light level at or below which the gloom takes hold. */
	private static final int MAX_LIGHT = 7;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (UmbralLight.inLowLight(player, MAX_LIGHT)) {
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
		AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute == null || attribute.getModifier(GLOOM_SPEED_ID) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(
			GLOOM_SPEED_ID, GLOOM_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	private static void remove(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute != null) {
			attribute.removeModifier(GLOOM_SPEED_ID);
		}
	}

	/** Exposed so the truth-table is anchored to the shared {@link FocusCondition} threshold helper. */
	static boolean holds(int lightLevel) {
		return FocusCondition.lowLightHolds(lightLevel, MAX_LIGHT);
	}
}
