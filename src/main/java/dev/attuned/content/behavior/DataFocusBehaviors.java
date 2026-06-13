package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusBehaviorDef;
import dev.attuned.api.focus.FocusCondition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * Builds runtime {@link FocusBehavior} instances from datapack-defined
 * {@link FocusBehaviorDef} palette entries.
 *
 * <p>This is the data half of the code-first-then-data resolution funnel: when no code
 * behavior is registered for a Focus's {@code behavior} id, {@code AttunedRegistries}
 * looks the id up in the {@code focus_behavior} registry and hands the definition here.
 *
 * <p>v1 supports {@code attuned:conditional_mob_effect} only. All palette behaviors are
 * passive (no Focus Ability).
 */
public final class DataFocusBehaviors {
	private DataFocusBehaviors() {}

	/** Builds a passive {@link FocusBehavior} for the given palette definition. */
	public static FocusBehavior build(FocusBehaviorDef definition) {
		return switch (definition) {
			case FocusBehaviorDef.ConditionalMobEffect effect -> new ConditionalMobEffectBehavior(effect);
		};
	}

	/**
	 * Refreshes a mob effect on the wearer for as long as the configured condition holds.
	 * When the condition stops holding the effect is left to lapse on its own short duration,
	 * which keeps the data behavior side-effect-symmetric with the shipped code behaviors
	 * (e.g. {@code TideBehavior}) without needing per-effect teardown bookkeeping.
	 */
	static final class ConditionalMobEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.ConditionalMobEffect def;

		ConditionalMobEffectBehavior(FocusBehaviorDef.ConditionalMobEffect def) {
			this.def = def;
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (!def.condition().test(player)) {
				return;
			}
			MobEffectInstance current = player.getEffect(def.effect());
			if (PassiveEffectRefresher.shouldRefresh(current, def.refreshTicks())) {
				player.addEffect(new MobEffectInstance(
					def.effect(), def.durationTicks(), def.amplifier(), true, false, false));
			}
		}

		FocusCondition condition() {
			return def.condition();
		}
	}
}
