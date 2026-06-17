package dev.attuned.pacts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Small passive bonuses while a pact is awake and its Tier 4 trial is complete. */
public final class PactTier4 {
	private PactTier4() {}

	private static final float RADIANT_UNDEAD_BONUS = 0.15F;
	private static final float NIGHTSWORN_DARK_DAMPEN = 0.15F;
	private static final float UNTETHERED_DECAY_MULTIPLIER = 0.90F;

	private static final int PYRESWORN_EXTRA_IGNITE_SECONDS = 1;
	private static final int FORGEBOUND_EXTRA_IGNITE_SECONDS = 1;
	private static final int TIDESWORN_EXTRA_SLOW_TICKS = 20;

	private static final ResourceLocation STONEHEART_TOUGHNESS_MODIFIER_ID =
		ResourceLocation.fromNamespaceAndPath("attuned", "stoneheart_tier4_toughness");

	public static int pyreswornIgniteSeconds(Player player) {
		return activeTier4(player, Pact.PYRESWORN)
			? Pacts.pyreswornIgniteSecondsBase() + PYRESWORN_EXTRA_IGNITE_SECONDS
			: Pacts.pyreswornIgniteSecondsBase();
	}

	public static int forgeboundIgniteSeconds(Player player) {
		return activeTier4(player, Pact.FORGEBOUND)
			? Pacts.forgeboundIgniteSecondsBase() + FORGEBOUND_EXTRA_IGNITE_SECONDS
			: Pacts.forgeboundIgniteSecondsBase();
	}

	public static int tideswornSlowTicks(Player player) {
		return activeTier4(player, Pact.TIDESWORN)
			? Pacts.tideswornSlowTicksBase() + TIDESWORN_EXTRA_SLOW_TICKS
			: Pacts.tideswornSlowTicksBase();
	}

	public static float radiantUndeadBonus(Player player) {
		return activeTier4(player, Pact.RADIANT_COVENANT) ? RADIANT_UNDEAD_BONUS : Pacts.radiantUndeadBonusBase();
	}

	public static float nightswornDarkDampen(Player player) {
		return activeTier4(player, Pact.NIGHTSWORN) ? NIGHTSWORN_DARK_DAMPEN : Pacts.nightswornDarkDampenBase();
	}

	public static int windrunnerSpeedAmplifier(Player player) {
		if (!activeTier4(player, Pact.WINDRUNNER) || !player.isSprinting()) {
			return 0;
		}
		return 1;
	}

	public static int wildrootRegenAmplifier(Player player) {
		return activeTier4(player, Pact.WILDROOT) ? 1 : 0;
	}

	public static float resonanceDecayMultiplier(Player player) {
		return activeTier4(player, Pact.UNTETHERED) ? UNTETHERED_DECAY_MULTIPLIER : 1.0F;
	}

	public static void reconcileStoneheartToughness(ServerPlayer player, Pact active) {
		AttributeInstance attr = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (attr == null) {
			return;
		}
		boolean shouldHave = active == Pact.STONEHEART && isTier4Complete(player, Pact.STONEHEART);
		boolean has = attr.getModifier(STONEHEART_TOUGHNESS_MODIFIER_ID) != null;
		if (shouldHave && !has) {
			attr.addTransientModifier(new AttributeModifier(
				STONEHEART_TOUGHNESS_MODIFIER_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
		} else if (!shouldHave && has) {
			attr.removeModifier(STONEHEART_TOUGHNESS_MODIFIER_ID);
		}
	}

	public static void removeStoneheartToughness(ServerPlayer player) {
		AttributeInstance attr = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (attr != null) {
			attr.removeModifier(STONEHEART_TOUGHNESS_MODIFIER_ID);
		}
	}

	private static boolean activeTier4(Player player, Pact pact) {
		return Pacts.isAt(player, pact) && isTier4Complete(player, pact);
	}

	private static boolean isTier4Complete(Player player, Pact pact) {
		return PactTrials.isTier4Complete(player, pact);
	}
}
