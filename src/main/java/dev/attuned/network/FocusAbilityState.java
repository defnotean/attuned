package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.combat.Apex;
import dev.attuned.pacts.PactTacticals;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.onboarding.Onboarding;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative state for the single active Focus Ability and cooldown. */
public final class FocusAbilityState {
	private static final int SYNC_INTERVAL_TICKS = 5;

	private static final Map<AbilityCooldownKey, Cooldown> COOLDOWNS = new HashMap<>();
	private static final Map<UUID, FocusAbilityStatusPayload> LAST_SENT = new HashMap<>();
	private static boolean initialized;

	private FocusAbilityState() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(FocusAbilityState::tick);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				// Force a resync but keep running cooldowns: /reload must not
				// hand every player a free cooldown reset. Stale ability ids
				// simply expire via pruneExpiredCooldowns.
				LAST_SENT.clear();
			}
		});
		AttunedServerCleanup.onStop(FocusAbilityState::clearCooldownState);
		AttunedPlayerCleanup.onForget(uuid -> {
			LAST_SENT.remove(uuid);
		});
	}

	private static void clearCooldownState() {
		COOLDOWNS.clear();
		LAST_SENT.clear();
	}

	public static void trigger(ServerPlayer player) {
		// Guard against forged/late packets: dead, removed, or spectating
		// players must never fire server-side ability effects.
		if (player.isRemoved() || player.isDeadOrDying() || player.isSpectator()) {
			return;
		}
		AbilitySelection selection = firstActiveAbility(player);
		if (selection == null) {
			// No active ability Focus: pact tactical, then an armed Apex capstone identity ability.
			if (PactTacticals.tryTrigger(player)) {
				return;
			}
			if (Apex.tryIdentityAbility(player)) {
				return;
			}
			ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
				Component.translatable("item.attuned.focus_ability.none"));
			sync(player, FocusAbilityStatusPayload.NO_ABILITY_SLOT, 0, 0);
			return;
		}

		int remaining = cooldownRemaining(player, selection);
		if (remaining > 0) {
			Cooldown cooldown = COOLDOWNS.get(selection.cooldownKey(player.getUUID()));
			ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
				Component.translatable(
					"item.attuned.focus_ability.cooldown", cooldownSecondsForMessage(remaining)));
			sync(player, selection.slot(), remaining,
				cooldown == null ? abilityCooldownTicks(selection.behavior(), player, selection.stack()) : cooldown.totalTicks());
			return;
		}

		boolean fired = runAbility(selection.behavior(), player, selection.stack());
		if (!fired) {
			sync(player, selection.slot(), 0, abilityCooldownTicks(selection.behavior(), player, selection.stack()));
			return;
		}

		Onboarding.tryAbilityHint(player);

		int total = abilityCooldownTicks(selection.behavior(), player, selection.stack());
		if (total > 0) {
			long endsAt = player.level().getGameTime() + total;
			COOLDOWNS.put(selection.cooldownKey(player.getUUID()), new Cooldown(endsAt, total));
			sync(player, selection.slot(), total, total);
		} else {
			COOLDOWNS.remove(selection.cooldownKey(player.getUUID()));
			sync(player, selection.slot(), 0, 0);
		}
	}

	static AbilitySelection firstActiveAbility(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inv.get(slot);
			FocusDefinition definition = Attunement.definitionFor(player, stack).orElse(null);
			if (definition == null) {
				continue;
			}
			ResourceLocation behaviorId = definition.behavior().orElse(null);
			if (behaviorId == null) {
				continue;
			}
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId, player.level().registryAccess());
			if (behavior != null && hasActiveAbility(behavior, player, stack)) {
				return new AbilitySelection(slot, stack, behavior, behaviorId.toString());
			}
		}
		return null;
	}

	static int cooldownRemaining(ServerPlayer player) {
		AbilitySelection selection = firstActiveAbility(player);
		return selection == null ? 0 : cooldownRemaining(player, selection);
	}

	static int cooldownRemaining(ServerPlayer player, AbilitySelection selection) {
		Cooldown cooldown = COOLDOWNS.get(selection.cooldownKey(player.getUUID()));
		if (cooldown == null) {
			return 0;
		}
		int remaining = (int) Math.max(0, cooldown.endsAt() - player.level().getGameTime());
		if (remaining <= 0) {
			COOLDOWNS.remove(selection.cooldownKey(player.getUUID()));
			return 0;
		}
		return remaining;
	}

	public static int cooldownSecondsForMessage(int remainingTicks) {
		if (remainingTicks <= 0) {
			return 0;
		}
		return (int) Math.max(1L, ((long) remainingTicks + 19L) / 20L);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		pruneExpiredCooldowns(now);
		if (now % SYNC_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AbilitySelection selection = firstActiveAbility(player);
			if (selection == null) {
				int pactRemaining = PactTacticals.remainingCooldown(player);
				if (pactRemaining > 0) {
					sync(player, FocusAbilityStatusPayload.PACT_TACTICAL_SLOT,
						pactRemaining, PactTacticals.COOLDOWN_TICKS);
				} else {
					sync(player, FocusAbilityStatusPayload.NO_ABILITY_SLOT, 0, 0);
				}
				continue;
			}
			int remaining = cooldownRemaining(player, selection);
			int total = 0;
			Cooldown cooldown = COOLDOWNS.get(selection.cooldownKey(player.getUUID()));
			if (cooldown != null) {
				total = cooldown.totalTicks();
			} else if (hasActiveAbility(selection.behavior(), player, selection.stack())) {
				total = abilityCooldownTicks(selection.behavior(), player, selection.stack());
			}
			sync(player, selection.slot(), remaining, total);
		}
	}

	private static void pruneExpiredCooldowns(long now) {
		COOLDOWNS.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
	}

	private static boolean hasActiveAbility(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {
		try {
			return behavior.hasActiveAbility();
		} catch (RuntimeException e) {
			logAbilityFailure("availability", behavior, player, stack, e);
			return false;
		}
	}

	private static int abilityCooldownTicks(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {
		try {
			return Math.max(0, behavior.abilityCooldownTicks());
		} catch (RuntimeException e) {
			logAbilityFailure("cooldown", behavior, player, stack, e);
			return 0;
		}
	}

	private static boolean runAbility(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {
		try {
			return behavior.onAbility(player, stack);
		} catch (RuntimeException e) {
			logAbilityFailure("execution", behavior, player, stack, e);
			return false;
		}
	}

	private static void logAbilityFailure(String phase, FocusBehavior behavior,
			ServerPlayer player, ItemStack stack, RuntimeException e) {
		Attuned.LOGGER.warn("Attuned Focus ability {} failed for {} using {} ({})",
			phase, player.getUUID(), stack.getItem(), behavior.getClass().getName(), e);
	}

	public static void syncStatus(ServerPlayer player, int slot, int remainingTicks, int totalTicks) {
		sync(player, slot, remainingTicks, totalTicks);
	}

	private static void sync(ServerPlayer player, int slot, int remainingTicks, int totalTicks) {
		FocusAbilityStatusPayload payload = new FocusAbilityStatusPayload(
			slot, Math.max(0, remainingTicks), Math.max(0, totalTicks));
		if (payload.equals(LAST_SENT.get(player.getUUID()))) {
			return;
		}
		LAST_SENT.put(player.getUUID(), payload);
		ServerPlayNetworking.send(player, new FocusAbilityStatusPayload(payload.slot(),
			payload.remainingTicks(), payload.totalTicks()));
	}

	record AbilitySelection(int slot, ItemStack stack, FocusBehavior behavior, String abilityId) {
		AbilityCooldownKey cooldownKey(UUID playerId) {
			return new AbilityCooldownKey(playerId, abilityId);
		}
	}

	private record AbilityCooldownKey(UUID playerId, String abilityId) {
	}

	private record Cooldown(long endsAt, int totalTicks) {
	}
}
