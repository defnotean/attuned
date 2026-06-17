package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Bloodfury Focus: the closer to death the wearer is, the faster they strike.
 *
 * <p>Each tick a transient attack-speed modifier is scaled by the fraction of
 * health missing — zero at full health, up to {@link #MAX_BONUS} at the brink.
 * The modifier is only rewritten when the amount actually changes, and removed
 * outright when the Focus deactivates.
 */
public final class BloodfuryBehavior implements FocusBehavior {

	private static final ResourceLocation MODIFIER_ID =
		ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "bloodfury");
	/** Attack-speed bonus at the brink of death (+40%). */
	private static final double MAX_BONUS = 0.40;

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed == null) {
			return;
		}
		float maxHealth = player.getMaxHealth();
		float missing = maxHealth <= 0.0F ? 0.0F : 1.0F - player.getHealth() / maxHealth;
		double bonus = MAX_BONUS * Mth.clamp(missing, 0.0F, 1.0F);

		AttributeModifier current = attackSpeed.getModifier(MODIFIER_ID);
		if (current != null && current.amount() == bonus) {
			return;
		}
		attackSpeed.removeModifier(MODIFIER_ID);
		if (bonus > 0.0) {
			attackSpeed.addTransientModifier(new AttributeModifier(
				MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed != null) {
			attackSpeed.removeModifier(MODIFIER_ID);
		}
	}
}
