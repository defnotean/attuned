package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusBehaviorDef;
import dev.attuned.api.focus.FocusCondition;
import dev.attuned.api.focus.ModifierEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * Builds runtime {@link FocusBehavior} instances from datapack-defined
 * {@link FocusBehaviorDef} palette entries.
 *
 * <p>This is the data half of the code-first-then-data resolution funnel: when no code
 * behavior is registered for a Focus's {@code behavior} id, {@code AttunedRegistries}
 * looks the id up in the {@code focus_behavior} registry and hands the definition here.
 *
 * <p>The shipped palette covers {@code attuned:conditional_mob_effect},
 * {@code attuned:on_hit_effect}, {@code attuned:periodic_effect}, and
 * {@code attuned:attribute_while}. All palette behaviors are passive (no Focus Ability).
 */
public final class DataFocusBehaviors {
	private DataFocusBehaviors() {}

	/** Stable id prefix for transient {@code attribute_while} modifiers. Distinct from
	 * {@code AttunedEffects}' {@code slot_N_mod_N} scheme so the two never collide. */
	private static final Identifier ATTRIBUTE_WHILE_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "palette_attr_while");

	/** Builds a passive {@link FocusBehavior} for the given palette definition. */
	public static FocusBehavior build(FocusBehaviorDef definition) {
		return switch (definition) {
			case FocusBehaviorDef.ConditionalMobEffect effect -> new ConditionalMobEffectBehavior(effect);
			case FocusBehaviorDef.OnHitEffect onHit -> new OnHitEffectBehavior(onHit);
			case FocusBehaviorDef.PeriodicEffect periodic -> new PeriodicEffectBehavior(periodic);
			case FocusBehaviorDef.AttributeWhile attributeWhile -> new AttributeWhileBehavior(attributeWhile);
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

	/**
	 * Applies a mob effect to the victim (or the attacker, when {@code target_self}) on a
	 * charged, hostile-only direct-melee hit. It does no per-tick work: the proc is driven by
	 * the existing combat {@code AFTER_DAMAGE} handler, which finds active Foci carrying this
	 * behavior and calls {@link #applyTo}. The charge and target gates live in the caller so the
	 * single set of {@code AttunedCombat}/{@code CombatTargets} predicates stays authoritative.
	 */
	public static final class OnHitEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.OnHitEffect def;

		OnHitEffectBehavior(FocusBehaviorDef.OnHitEffect def) {
			this.def = def;
		}

		/** The charge fraction (0–1) a swing must reach before the effect procs. */
		public float chargeThreshold() {
			return def.chargeThreshold();
		}

		/** Whether the proc may only land on a hostile mob or valid PvP opponent. */
		public boolean hostileOnly() {
			return def.hostileOnly();
		}

		/** Whether the effect lands on the attacker instead of the victim. */
		public boolean targetSelf() {
			return def.targetSelf();
		}

		/** Picks the recipient and applies the configured effect to it. */
		public void applyTo(ServerPlayer attacker, LivingEntity victim) {
			LivingEntity recipient = def.targetSelf() ? attacker : victim;
			recipient.addEffect(new MobEffectInstance(def.effect(), def.durationTicks(), def.amplifier()));
		}
	}

	/**
	 * Keeps a flat mob effect refreshed on the wearer on a fixed cadence — an unconditional
	 * buff. Reuses the shared {@link PassiveEffectRefresher} so the apply flags (ambient,
	 * hidden, icon-less) match the rest of the passive behaviors.
	 */
	static final class PeriodicEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.PeriodicEffect def;

		PeriodicEffectBehavior(FocusBehaviorDef.PeriodicEffect def) {
			this.def = def;
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (player.tickCount % def.refreshTicks() != 0) {
				return;
			}
			PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier(),
				true, false, false);
		}
	}

	/**
	 * Adds a transient attribute modifier while the condition holds and removes it when it stops,
	 * flipping it under a stable id so toggling is idempotent. {@code onDeactivate} removes the
	 * modifier unconditionally, so unequipping the Focus while the condition still holds never
	 * strands the modifier.
	 */
	static final class AttributeWhileBehavior implements FocusBehavior {
		private final FocusBehaviorDef.AttributeWhile def;

		AttributeWhileBehavior(FocusBehaviorDef.AttributeWhile def) {
			this.def = def;
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			ModifierEntry modifier = def.modifier();
			AttributeInstance ai = player.getAttribute(modifier.attribute());
			if (ai == null) {
				return;
			}
			boolean present = ai.getModifier(ATTRIBUTE_WHILE_MODIFIER_ID) != null;
			if (def.condition().test(player)) {
				if (!present) {
					ai.addTransientModifier(new AttributeModifier(
						ATTRIBUTE_WHILE_MODIFIER_ID, modifier.amount(), modifier.operation()));
				}
			} else if (present) {
				ai.removeModifier(ATTRIBUTE_WHILE_MODIFIER_ID);
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			AttributeInstance ai = player.getAttribute(def.modifier().attribute());
			if (ai != null) {
				ai.removeModifier(ATTRIBUTE_WHILE_MODIFIER_ID);
			}
		}

		FocusCondition condition() {
			return def.condition();
		}
	}
}
