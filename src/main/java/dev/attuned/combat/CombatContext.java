package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Short-lived state shared by Attuned systems while one damage event is shaped. */
public final class CombatContext {
	private final LivingEntity defender;
	private final LivingEntity attacker;
	private final PlayerState defenderState;
	private final PlayerState attackerState;

	private CombatContext(LivingEntity defender, LivingEntity attacker, PlayerState defenderState,
			PlayerState attackerState) {
		this.defender = defender;
		this.attacker = attacker;
		this.defenderState = defenderState;
		this.attackerState = attackerState;
	}

	public static CombatContext of(LivingEntity defender, DamageSource source) {
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		PlayerState defenderState = defender instanceof Player defenderPlayer
			? playerState(defenderPlayer)
			: null;
		PlayerState attackerState = attacker instanceof Player attackerPlayer
			? attacker == defender && defenderState != null ? defenderState : playerState(attackerPlayer)
			: null;
		return new CombatContext(defender, attacker, defenderState, attackerState);
	}

	public LivingEntity attacker() {
		return attacker;
	}

	public Optional<Affinity> affinityOf(LivingEntity entity) {
		if (entity instanceof Player player) {
			return stateOf(player).committed();
		}
		return MobAffinities.of(entity);
	}

	public boolean isDiscord(LivingEntity entity) {
		return entity instanceof Player player && stateOf(player).discord();
	}

	public boolean hasAffinityPressure(LivingEntity entity) {
		return entity instanceof Player player
			? stateOf(player).hasAffinityPressure()
			: MobAffinities.of(entity).isPresent();
	}

	public boolean hasActiveFocus(Player player, Identifier targetId) {
		return stateOf(player).hasActiveFocus(targetId);
	}

	public Optional<Apex.Capstone> capstoneOf(Player player) {
		return stateOf(player).capstone();
	}

	public boolean isAt(Player player, Apex.Capstone capstone) {
		return stateOf(player).isAt(capstone);
	}

	public boolean atApex(Player player) {
		return stateOf(player).atApex();
	}

	public Map<Affinity, Integer> activeAffinityCounts(Player player) {
		return stateOf(player).activeAffinityCounts();
	}

	private PlayerState stateOf(Player player) {
		if (player == defender && defenderState != null) {
			return defenderState;
		}
		if (player == attacker && attackerState != null) {
			return attackerState;
		}
		return playerState(player);
	}

	public record PlayerState(Optional<Affinity> committed, boolean discord,
			Optional<Apex.Capstone> capstone, float resonance,
			Map<Affinity, Integer> activeAffinityCounts, Set<Identifier> activeFocusIds) {
		public boolean atApex() {
			return resonance >= Resonance.APEX_THRESHOLD;
		}

		public boolean isAt(Apex.Capstone target) {
			return capstone.filter(value -> value == target).isPresent();
		}

		public boolean hasAffinityPressure() {
			return committed.isPresent() || discord;
		}

		public boolean hasActiveFocus(Identifier targetId) {
			return activeFocusIds.contains(targetId);
		}
	}

	private static PlayerState playerState(Player player) {
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		List<Integer> activeSlots = resolution.activeSlots();
		List<Optional<Affinity>> orderedActiveAffinities = new ArrayList<>(activeSlots.size());
		EnumMap<Affinity, Integer> activeAffinityCounts = new EnumMap<>(Affinity.class);
		Set<Identifier> activeFocusIds = new HashSet<>();
		Set<Affinity> activeAffinities = EnumSet.noneOf(Affinity.class);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int used = 0;
		for (int slot : activeSlots) {
			ItemStack stack = inv.get(slot);
			Optional<FocusDefinition> maybeDefinition = Attunement.definitionFor(player, stack);
			if (maybeDefinition.isEmpty()) {
				orderedActiveAffinities.add(Optional.empty());
				continue;
			}
			FocusDefinition definition = maybeDefinition.get();
			activeFocusIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
			used += definition.cost();
			Optional<Affinity> affinity = definition.affinity();
			orderedActiveAffinities.add(affinity);
			affinity.ifPresent(activeAffinity -> {
				activeAffinities.add(activeAffinity);
				activeAffinityCounts.merge(activeAffinity, 1, Integer::sum);
			});
		}
		Optional<Affinity> committed = activeAffinities.size() == 1
			? Optional.of(activeAffinities.iterator().next())
			: Optional.empty();
		boolean discord = activeAffinities.size() >= 2;
		return new PlayerState(committed, discord,
			Apex.resolveCapstone(orderedActiveAffinities, used, Attunement.capacity(player)),
			Resonance.get(player), Map.copyOf(activeAffinityCounts), Set.copyOf(activeFocusIds));
	}
}
