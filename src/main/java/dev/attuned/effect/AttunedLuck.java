package dev.attuned.effect;

import dev.attuned.api.focus.ModifierEntry;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Shared clamps for Attuned-owned vanilla Luck modifiers. */
public final class AttunedLuck {
	public static final double MAX_ATTUNED_LUCK_BONUS = 3.0D;

	private AttunedLuck() {}

	public static boolean isPositiveAddLuck(ModifierEntry entry) {
		return entry.attribute().equals(Attributes.LUCK)
			&& entry.operation() == AttributeModifier.Operation.ADD_VALUE
			&& entry.amount() > 0.0D;
	}

	public static double cappedPositiveAddLuck(AttributeInstance luck, double requested) {
		if (luck == null || requested <= 0.0D) {
			return requested;
		}
		return Math.min(requested, Math.max(0.0D, MAX_ATTUNED_LUCK_BONUS - currentPositiveAttunedLuck(luck)));
	}

	private static double currentPositiveAttunedLuck(AttributeInstance luck) {
		double total = 0.0D;
		for (AttributeModifier modifier : luck.getModifiers()) {
			if (isPositiveAttunedLuck(modifier)) {
				total += modifier.amount();
			}
		}
		return total;
	}

	private static boolean isPositiveAttunedLuck(AttributeModifier modifier) {
		return modifier.name().startsWith("attuned:")
			&& modifier.operation() == AttributeModifier.Operation.ADD_VALUE
			&& modifier.amount() > 0.0D;
	}
}
