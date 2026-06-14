package dev.attuned.content;

/**
 * The pure rules for Focus tempering: reweaving two copies of the same Focus
 * into one stronger "Tempered" copy.
 *
 * <p>Kept free of any Minecraft type so the policy can be unit-tested in
 * isolation. The Altar of Reweaving gathers the two input ids and their tempered
 * state and delegates the decision here; the effect and budget layers apply the
 * {@code +25%} modifier boost and the {@link #temperedCost} surcharge separately.
 */
public final class TemperingResolver {
	private TemperingResolver() {}

	/** Extra attunement a tempered Focus costs on top of its base cost. */
	public static final int TEMPER_COST_SURCHARGE = 1;

	/**
	 * Whether two Focus inputs may be tempered into one. Tempering needs two
	 * untempered copies of the very same (non-empty) Focus.
	 *
	 * @param idA       the first input's Focus id
	 * @param idB       the second input's Focus id
	 * @param aTempered whether the first input is already tempered
	 * @param bTempered whether the second input is already tempered
	 */
	public static boolean canTemper(String idA, String idB, boolean aTempered, boolean bTempered) {
		if (idA == null || idA.isEmpty()) {
			return false;
		}
		if (!idA.equals(idB)) {
			return false;
		}
		return !aTempered && !bTempered;
	}

	/** The attunement cost of a tempered Focus given its untempered base cost. */
	public static int temperedCost(int baseCost) {
		return baseCost + TEMPER_COST_SURCHARGE;
	}
}
