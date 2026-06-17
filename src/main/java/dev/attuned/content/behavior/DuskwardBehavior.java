package dev.attuned.content.behavior;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusCondition;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Duskward Focus (Umbral Eclipse): grants extra armor while the wearer stands in low light.
 * The defensive twin of {@link GloomstrideBehavior} — a flat armor modifier flipped on while
 * the dark holds and stripped the moment it lifts (or the Focus deactivates), under a stable
 * id so the toggle is idempotent.
 */
public final class DuskwardBehavior implements FocusBehavior {
	private static final ResourceLocation DUSK_ARMOR_ID =
		new ResourceLocation(Attuned.MOD_ID, "duskward_focus_dark_armor");
	private static final double DUSK_ARMOR = 3.0;
	/** Light level at or below which the dusk-ward hardens. */
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
		AttributeInstance attribute = player.getAttribute(Attributes.ARMOR);
		if (attribute == null || attribute.getModifier(AttributeModifierIds.uuid(DUSK_ARMOR_ID)) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(DUSK_ARMOR_ID), AttributeModifierIds.name(DUSK_ARMOR_ID), DUSK_ARMOR, AttributeModifier.Operation.ADDITION));
	}

	private static void remove(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.ARMOR);
		if (attribute != null) {
			attribute.removeModifier(AttributeModifierIds.uuid(DUSK_ARMOR_ID));
		}
	}

	/** Exposed so the truth-table is anchored to the shared {@link FocusCondition} threshold helper. */
	static boolean holds(int lightLevel) {
		return FocusCondition.lowLightHolds(lightLevel, MAX_LIGHT);
	}
}
