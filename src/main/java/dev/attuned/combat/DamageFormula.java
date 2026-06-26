package dev.attuned.combat;

/** Pure helpers for Attuned's ordered damage-shaping pipeline. */
public final class DamageFormula {
	private DamageFormula() {}

	public static float multiply(float amount, float multiplier) {
		return amount * multiplier;
	}

	public static float amplify(float amount, float bonusFraction) {
		return multiply(amount, 1.0F + bonusFraction);
	}

	public static float dampen(float amount, float dampenFraction) {
		return multiply(amount, 1.0F - dampenFraction);
	}

	public static float cap(float amount, float maximum) {
		return Math.min(amount, maximum);
	}

	public static float floor(float amount, float minimum) {
		return Math.max(amount, minimum);
	}
}
