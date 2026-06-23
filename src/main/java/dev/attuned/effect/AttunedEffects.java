package dev.attuned.effect;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.compat.ParticleCompat;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.Attuned;
import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.focus.ModifierEntry;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.content.AttunedComponents;
import dev.attuned.onboarding.Onboarding;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies and removes Focus effects as a player's active attunement set changes,
 * and shows a subtle particle aura while a player has any active Focus.
 *
 * <p>Each server tick the active slots are diffed against the previous tick's
 * tracked state: newly-active (or changed) slots have their declarative attribute
 * modifiers applied and their {@link FocusBehavior} activated; slots that went
 * dormant (or changed) have theirs removed; slots that are unchanged get a
 * {@link FocusBehavior#onTick} call. Attribute modifiers are transient — they are
 * never persisted, so a full reapply on respawn is both correct and required.
 */
public final class AttunedEffects {
	private AttunedEffects() {}

	/** Ticks between aura particle bursts. */
	private static final int AURA_INTERVAL = 16;

	/** Strength multiplier applied to every attribute modifier of a Tempered Focus. */
	private static final double TEMPERED_MODIFIER_MULTIPLIER = 1.25; // * 1.25

	/**
	 * Per-player snapshot of which Focus effects were active last tick. Each value
	 * stores the exact stack plus the definition payload that was applied, so
	 * datapack reloads cannot make teardown look up a different modifier set.
	 */
	private static final Map<UUID, Map<Integer, AppliedFocus>> ACTIVE = new HashMap<>();
	/** Per-player dormant slot set from last tick, for one-shot dormancy hints. */
	private static final Map<UUID, Set<Integer>> DORMANT = new HashMap<>();

	/** Server-wide tick counter used to throttle the aura. */
	private static int auraTick;
	private static boolean initialized;

	/** Registers the tick and respawn hooks that drive the effect system. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			auraTick++;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});

		// A respawned player is a fresh entity with no transient modifiers; tear
		// down the old entity, then reapply Foci from scratch on the next tick.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			deactivateTrackedFoci(oldPlayer);
			ACTIVE.remove(newPlayer.getUUID());
			DORMANT.remove(newPlayer.getUUID());
		});

		// A disconnecting player will not tick again, so active behavior teardown
		// must run here rather than waiting for a future diff.
		AttunedPlayerCleanup.onForgetPlayer(AttunedEffects::deactivateTrackedFoci);
		AttunedServerCleanup.onStopServer(AttunedEffects::deactivateAllTrackedFoci);
	}

	private static void tickPlayer(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		List<Integer> currentActive = resolution.activeSlots();
		Map<Integer, FocusDefinition> activeDefinitions = activeDefinitions(player, inv, currentActive);
		boolean wasTracked = ACTIVE.containsKey(player.getUUID());
		Map<Integer, AppliedFocus> tracked =
			ACTIVE.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		Set<Affinity> activeAffinities = activeAffinities(activeDefinitions.values());

		// A subtle ambient aura while the player has anything active, plus small
		// custom visual motifs for specific active Foci.
		// Elytra flight already fills the screen; skip head-height aura bursts.
		if (!currentActive.isEmpty() && !player.isFallFlying() && auraTick % AURA_INTERVAL == 0) {
			spawnAura(player, activeAffinities);
			FocusVisualEffects.spawn(player, inv, currentActive, auraTick);
		}

		// Build this tick's active snapshot from the currently-loaded definitions.
		Map<Integer, AppliedFocus> nextState = new HashMap<>();
		for (int slot : currentActive) {
			if (activeDefinitions.containsKey(slot)) {
				AppliedFocus now = appliedFocusFor(inv.get(slot), activeDefinitions.get(slot));
				nextState.put(slot, now);
			}
		}
		Map<Integer, BudgetResolver.DormantReason> dormantReasons = resolution.dormantReasons();
		if (!currentActive.isEmpty()) {
			AttunedAdvancements.award(player, "attunement/first_focus");
		}
		if (!dormantReasons.isEmpty()) {
			AttunedAdvancements.award(player, "attunement/first_dormant_focus");
		}
		if (activeAffinities.size() >= 2) {
			AttunedAdvancements.award(player, "attunement/first_discord");
		}
		announceNewDormantSlots(player, dormantReasons, wasTracked);

		boolean anyActivated = false;
		boolean anyDeactivated = false;

		// Removals: slots that were active last tick but are no longer active, or
		// whose stack/definition payload changed.
		for (Map.Entry<Integer, AppliedFocus> entry : tracked.entrySet()) {
			int slot = entry.getKey();
			AppliedFocus previous = entry.getValue();
			AppliedFocus now = nextState.get(slot);
			if (now == null || !sameAppliedFocus(previous, now)) {
				removeFocus(player, slot, previous);
				anyDeactivated = true;
			}
		}

		// Applications and ticks: walk the currently-active slots.
		for (int slot : currentActive) {
			AppliedFocus now = nextState.get(slot);
			if (now == null) {
				continue;
			}
			AppliedFocus previous = tracked.get(slot);
			if (previous != null && sameAppliedFocus(previous, now)) {
				// Unchanged: still active with the same applied effect.
				tickFocus(player, now);
			} else {
				// Newly active, or the stack/definition payload in this slot changed.
				applyFocus(player, slot, now);
				anyActivated = true;
			}
		}

		// Commit this tick's already-defensive snapshot for next-tick diffing.
		tracked.clear();
		tracked.putAll(nextState);

		// Chime for real activation changes — but stay silent on the first-tick
		// reapply after a login or respawn, when every active Focus reads as new.
		if (wasTracked) {
			if (anyActivated) {
				playAttuneSound(player, 1.2F);
			}
			if (anyDeactivated) {
				playAttuneSound(player, 0.7F);
			}
		}
	}

	private static void announceNewDormantSlots(ServerPlayer player,
			Map<Integer, BudgetResolver.DormantReason> dormantReasons, boolean wasTracked) {
		UUID id = player.getUUID();
		Set<Integer> previous = DORMANT.getOrDefault(id, Set.of());
		if (wasTracked) {
			for (Map.Entry<Integer, BudgetResolver.DormantReason> entry : dormantReasons.entrySet()) {
				int slot = entry.getKey();
				if (!previous.contains(slot)) {
					PlayerMessages.system(player, Component.literal("A Focus falls dormant: ")
						.withStyle(ChatFormatting.GRAY)
						.append(AttunedAttachments.getInventory(player).get(slot).getHoverName())
						.append(Component.literal(". " + dormantChatMessage(entry.getValue()))
							.withStyle(ChatFormatting.DARK_GRAY)));
					Onboarding.tryDormantHint(player);
					break;
				}
			}
		}
		if (dormantReasons.isEmpty()) {
			DORMANT.remove(id);
		} else {
			DORMANT.put(id, Set.copyOf(dormantReasons.keySet()));
		}
	}

	private static String dormantChatMessage(BudgetResolver.DormantReason reason) {
		return switch (reason) {
			case NOT_ENOUGH_CAPACITY -> "Not enough remaining capacity.";
			case DUPLICATE_UNIQUE -> "Only one copy can be active.";
		};
	}

	private static Map<Integer, FocusDefinition> activeDefinitions(ServerPlayer player,
			AttunedInv inv, List<Integer> activeSlots) {
		Map<Integer, FocusDefinition> definitions = new HashMap<>();
		for (int slot : activeSlots) {
			Attunement.definitionFor(player, inv.get(slot))
				.ifPresent(definition -> definitions.put(slot, definition));
		}
		return definitions;
	}

	private static Set<Affinity> activeAffinities(Iterable<FocusDefinition> activeDefinitions) {
		Set<Affinity> affinities = EnumSet.noneOf(Affinity.class);
		for (FocusDefinition definition : activeDefinitions) {
			definition.affinity().ifPresent(affinities::add);
		}
		return affinities;
	}

	private static Optional<Affinity> committedAffinity(Set<Affinity> activeAffinities) {
		return activeAffinities.size() == 1 ? Optional.of(activeAffinities.iterator().next()) : Optional.empty();
	}

	/** A soft chime when a Focus changes activation — pitched up to attune, down to lapse. */
	private static void playAttuneSound(ServerPlayer player, float pitch) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, pitch);
	}

	/** Stable, per-slot, per-modifier-index id so modifiers can be removed precisely. */
	private static ResourceLocation modifierId(int slot, int index) {
		return new ResourceLocation(Attuned.MOD_ID, "slot_" + slot + "_mod_" + index);
	}

	private static AppliedFocus appliedFocusFor(ItemStack stack, FocusDefinition def) {
		return new AppliedFocus(stack.copy(), List.copyOf(def.modifiers()), def.behavior());
	}

	private static boolean sameAppliedFocus(AppliedFocus previous, AppliedFocus now) {
		return ItemStack.matches(previous.stack(), now.stack())
			&& previous.modifiers().equals(now.modifiers())
			&& previous.behavior().equals(now.behavior());
	}

	private static void applyFocus(ServerPlayer player, int slot, AppliedFocus focus) {
		// A Tempered Focus amplifies every declarative attribute modifier. Removal
		// keys off the slot/index id, never the amount, so the boosted modifier is
		// still torn down exactly when the slot goes dormant.
		boolean tempered = focus.stack().has(AttunedComponents.TEMPERED);
		for (int i = 0; i < focus.modifiers().size(); i++) {
			ModifierEntry entry = focus.modifiers().get(i);
			AttributeInstance ai = player.getAttribute(entry.attribute());
			if (ai == null) {
				continue;
			}
			ResourceLocation id = modifierId(slot, i);
			if (ai.getModifier(AttributeModifierIds.uuid(id)) == null) {
				double amount = tempered ? entry.amount() * TEMPERED_MODIFIER_MULTIPLIER : entry.amount();
				if (AttunedLuck.isPositiveAddLuck(entry)) {
					amount = AttunedLuck.cappedPositiveAddLuck(ai, amount);
					if (amount <= 0.0D) {
						continue;
					}
				}
				ai.addTransientModifier(new AttributeModifier(
					AttributeModifierIds.uuid(id), AttributeModifierIds.name(id), amount, entry.operation()));
			}
		}

		focus.behavior().ifPresent(behaviorId -> {
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId, player.registryAccess());
			if (behavior != null) {
				runBehaviorActivate(behavior, player, focus.stack());
			}
		});
	}

	private static void removeFocus(ServerPlayer player, int slot, AppliedFocus focus) {
		for (int i = 0; i < focus.modifiers().size(); i++) {
			ModifierEntry entry = focus.modifiers().get(i);
			AttributeInstance ai = player.getAttribute(entry.attribute());
			if (ai == null) {
				continue;
			}
			ai.removeModifier(AttributeModifierIds.uuid(modifierId(slot, i)));
		}

		focus.behavior().ifPresent(behaviorId -> {
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId, player.registryAccess());
			if (behavior != null) {
				runBehaviorDeactivate(behavior, player, focus.stack());
			}
		});
	}

	private static void deactivateAllTrackedFoci(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			deactivateTrackedFoci(player);
		}
		ACTIVE.clear();
		DORMANT.clear();
		auraTick = 0;
	}

	private static void deactivateTrackedFoci(ServerPlayer player) {
		UUID id = player.getUUID();
		Map<Integer, AppliedFocus> tracked = ACTIVE.remove(id);
		DORMANT.remove(id);
		if (tracked == null) {
			return;
		}
		for (Map.Entry<Integer, AppliedFocus> entry : tracked.entrySet()) {
			try {
				removeFocus(player, entry.getKey(), entry.getValue());
			} catch (RuntimeException e) {
				Attuned.LOGGER.warn("Attuned Focus deactivation failed for {} slot {}",
					id, entry.getKey(), e);
			}
		}
	}

	private static void tickFocus(ServerPlayer player, AppliedFocus focus) {
		focus.behavior()
			.map(behaviorId -> AttunedRegistries.getBehavior(behaviorId, player.registryAccess()))
			.ifPresent(behavior -> runBehaviorTick(behavior, player, focus.stack()));
	}

	private record AppliedFocus(ItemStack stack, List<ModifierEntry> modifiers, Optional<ResourceLocation> behavior) {}

	private static void runBehaviorActivate(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {
		try {
			behavior.onActivate(player, stack);
		} catch (RuntimeException e) {
			logBehaviorFailure("activation", behavior, player, stack, e);
		}
	}

	private static void runBehaviorDeactivate(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {
		try {
			behavior.onDeactivate(player, stack);
		} catch (RuntimeException e) {
			logBehaviorFailure("deactivation", behavior, player, stack, e);
		}
	}

	private static void runBehaviorTick(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {
		try {
			behavior.onTick(player, stack);
		} catch (RuntimeException e) {
			logBehaviorFailure("tick", behavior, player, stack, e);
		}
	}

	private static void logBehaviorFailure(String phase, FocusBehavior behavior,
			ServerPlayer player, ItemStack stack, RuntimeException e) {
		Attuned.LOGGER.warn("Attuned Focus behavior {} failed for {} using {} ({})",
			phase, player.getUUID(), stack.getItem(), behavior.getClass().getName(), e);
	}

	/** A subtle ambient particle aura shown while the player has any active Focus. */
	private static void spawnAura(ServerPlayer player, Set<Affinity> activeAffinities) {
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(auraParticle(activeAffinities),
			player.getX(), player.getY() + 1.0, player.getZ(),
			6, 0.4, 0.9, 0.4, 0.05);
	}

	/** The aura particle for a player's stance: affinity-coloured, Discord magenta, or neutral. */
	private static ParticleOptions auraParticle(Set<Affinity> activeAffinities) {
		if (activeAffinities.size() >= 2) {
			return ParticleCompat.dust(AffinityColors.DISCORD_RGB, 1.0F);
		}
		Optional<Affinity> affinity = committedAffinity(activeAffinities);
		if (affinity.isPresent()) {
			return ParticleCompat.dust(affinity.get().argb() & 0x00FFFFFF, 1.0F);
		}
		return ParticleTypes.WITCH;
	}
}
