package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative state for the single active Focus Ability and cooldown. */
public final class FocusAbilityState {
	private static final int SYNC_INTERVAL_TICKS = 5;

	private static final Map<UUID, Cooldown> COOLDOWNS = new HashMap<>();
	private static final Map<UUID, FocusAbilityStatusPayload> LAST_SENT = new HashMap<>();
	private static boolean initialized;

	private FocusAbilityState() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(FocusAbilityState::tick);
		AttunedServerCleanup.onStop(() -> {
			COOLDOWNS.clear();
			LAST_SENT.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			COOLDOWNS.remove(uuid);
			LAST_SENT.remove(uuid);
		});
	}

	public static void trigger(ServerPlayer player) {
		AbilitySelection selection = firstActiveAbility(player);
		if (selection == null) {
			player.sendOverlayMessage(Component.translatable("item.attuned.focus_ability.none"));
			sync(player, FocusAbilityStatusPayload.NO_ABILITY_SLOT, 0, 0);
			return;
		}

		int remaining = cooldownRemaining(player);
		if (remaining > 0) {
			Cooldown cooldown = COOLDOWNS.get(player.getUUID());
			player.sendOverlayMessage(Component.translatable(
				"item.attuned.focus_ability.cooldown", Math.max(1, (remaining + 19) / 20)));
			sync(player, selection.slot(), remaining, cooldown.totalTicks());
			return;
		}

		boolean fired = runAbility(selection.behavior(), player, selection.stack());
		if (!fired) {
			sync(player, selection.slot(), 0, abilityCooldownTicks(selection.behavior(), player, selection.stack()));
			return;
		}

		int total = abilityCooldownTicks(selection.behavior(), player, selection.stack());
		if (total > 0) {
			long endsAt = player.level().getGameTime() + total;
			COOLDOWNS.put(player.getUUID(), new Cooldown(endsAt, total));
			sync(player, selection.slot(), total, total);
		} else {
			COOLDOWNS.remove(player.getUUID());
			sync(player, selection.slot(), 0, 0);
		}
	}

	static AbilitySelection firstActiveAbility(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inv.get(slot);
			FocusBehavior behavior = Attunement.definitionFor(player, stack)
				.flatMap(FocusDefinition::behavior)
				.map(AttunedRegistries::getBehavior)
				.orElse(null);
			if (behavior != null && hasActiveAbility(behavior, player, stack)) {
				return new AbilitySelection(slot, stack, behavior);
			}
		}
		return null;
	}

	static int cooldownRemaining(ServerPlayer player) {
		Cooldown cooldown = COOLDOWNS.get(player.getUUID());
		if (cooldown == null) {
			return 0;
		}
		int remaining = (int) Math.max(0, cooldown.endsAt() - player.level().getGameTime());
		if (remaining <= 0) {
			COOLDOWNS.remove(player.getUUID());
			return 0;
		}
		return remaining;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % SYNC_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AbilitySelection selection = firstActiveAbility(player);
			if (selection == null) {
				sync(player, FocusAbilityStatusPayload.NO_ABILITY_SLOT, 0, 0);
				continue;
			}
			int remaining = cooldownRemaining(player);
			int total = 0;
			Cooldown cooldown = COOLDOWNS.get(player.getUUID());
			if (cooldown != null) {
				total = cooldown.totalTicks();
			} else if (hasActiveAbility(selection.behavior(), player, selection.stack())) {
				total = abilityCooldownTicks(selection.behavior(), player, selection.stack());
			}
			sync(player, selection.slot(), remaining, total);
		}
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

	record AbilitySelection(int slot, ItemStack stack, FocusBehavior behavior) {
	}

	private record Cooldown(long endsAt, int totalTicks) {
	}
}
