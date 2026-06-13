package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dreadfang Focus (Umbral Eclipse): on a fully charged direct-melee hit against a hostile mob
 * or valid PvP opponent, the victim is plunged into vanilla {@code minecraft:darkness} for a
 * short window. It does no per-tick work — the proc is driven by the existing combat
 * {@code AFTER_DAMAGE} handler in {@code AttunedCombat}, which finds the active Dreadfang Focus
 * and calls {@link #applyTo}. The charge and target gates live in the caller so the single set
 * of authoritative combat predicates (charged direct melee, hostile/PvP) stays in one place,
 * exactly like Sunlance and Temper.
 */
public final class DreadfangBehavior implements FocusBehavior {
	/** Dreadfang asks for a deliberate, fully charged swing rather than spam-click pressure. */
	public static final float CHARGED_SWING_THRESHOLD = 0.9F;
	/** How long the victim is blinded by the dark, in ticks (~3 seconds). */
	private static final int DARKNESS_TICKS = 60;

	/** Plunges the victim into vanilla darkness for the designed window. */
	public static void applyTo(LivingEntity victim) {
		victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0, true, false, true));
	}
}
