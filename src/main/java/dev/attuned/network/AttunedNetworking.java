package dev.attuned.network;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.content.behavior.UpdraftBehavior;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Attuned networking: the Focus Ability channel and the affinity-inspect channel.
 *
 * <p>When a player presses the ability keybind the client sends an
 * {@link AbilityPayload}; the server then triggers {@link FocusAbilityState#trigger}
 * on the first active Focus that explicitly owns the ability key. The payload
 * carries no data and all validation is server-side, so a forged payload only
 * ever fires the single ability the player legitimately has active.</p>
 *
 * <p>When a player crouches and looks at another player for ~1.5s the client sends
 * one {@link InspectRequestPayload}; the server re-validates range and line of
 * sight, rate-limits the requester, and reports the target's committed affinity,
 * Discord stance, and Apex state on the requester's action bar.</p>
 */
public final class AttunedNetworking {
	/** Maximum requester-to-target distance, in blocks, for an inspect to resolve. */
	private static final double INSPECT_RANGE_BLOCKS = 24.0;
	/** Minimum ticks between accepted inspect requests from one requester. */
	private static final int INSPECT_COOLDOWN_TICKS = 20;
	/** Maximum accepted Updraft lift heartbeats per player, in ticks. */
	private static final int LIFT_HEARTBEAT_TICKS = 10;

	/** Per-requester game-time of the last accepted inspect, for rate limiting. */
	private static final Map<UUID, Long> LAST_INSPECT = new HashMap<>();
	/** Per-player game-time of the last accepted Updraft lift packet. */
	private static final Map<UUID, Long> LAST_LIFT = new HashMap<>();
	private static boolean initialized;

	private AttunedNetworking() {}

	/** Registers the ability and inspect payload types and their server-side receivers. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		CircleNetworking.init();
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, player, sender) -> {
			player.getLevel().getServer().execute(() -> FocusAbilityState.trigger(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(InspectRequestPayload.TYPE, (payload, player, sender) -> {
			int targetId = payload.targetEntityId();
			player.getLevel().getServer().execute(() -> handleInspect(player, targetId));
		});

		AttunedServerCleanup.onStop(LAST_INSPECT::clear);
		AttunedPlayerCleanup.onForget(LAST_INSPECT::remove);
		AttunedServerCleanup.onStop(LAST_LIFT::clear);
		AttunedPlayerCleanup.onForget(LAST_LIFT::remove);

		ServerPlayNetworking.registerGlobalReceiver(UpdraftLiftPayload.TYPE, (payload, player, sender) -> {
			player.getLevel().getServer().execute(() -> handleUpdraftLift(player, payload));
		});

		FocusAbilityState.init();
	}

	private static void handleUpdraftLift(ServerPlayer player, UpdraftLiftPayload payload) {
		long now = player.getLevel().getGameTime();
		boolean deactivating = !payload.boosting() && !payload.braking();
		Long lastLift = LAST_LIFT.get(player.getUUID());
		if (!deactivating && lastLift != null && now - lastLift < LIFT_HEARTBEAT_TICKS) {
			return;
		}
		// Also require an actual glide: lift controls from a client that is
		// not fall-flying must never arm the fall-damage mitigation path.
		if (!UpdraftBehavior.isActive(player) || !UpdraftBehavior.hasFunctionalElytra(player)
				|| !player.isFallFlying()) {
			UpdraftBehavior.setControls(player.getUUID(), false, false);
			return;
		}
		LAST_LIFT.put(player.getUUID(), now);
		UpdraftBehavior.setControls(player.getUUID(), payload.boosting(), payload.braking());
	}

	// Resolves the inspected target on the server thread, validates range + line of
	// sight + rate limit, then reports the target's public stance to the requester.
	private static void handleInspect(ServerPlayer player, int targetEntityId) {
		ServerLevel level = player.getLevel();
		long now = level.getGameTime();
		Long last = LAST_INSPECT.get(player.getUUID());
		if (last != null && now - last < INSPECT_COOLDOWN_TICKS) {
			return;
		}

		Entity entity = level.getEntity(targetEntityId);
		if (!(entity instanceof ServerPlayer target) || target == player || !target.isAlive()) {
			return;
		}
		if (player.distanceToSqr(target) > INSPECT_RANGE_BLOCKS * INSPECT_RANGE_BLOCKS) {
			return;
		}
		if (!player.hasLineOfSight(target)) {
			return;
		}

		LAST_INSPECT.put(player.getUUID(), now);
		ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,
			inspectLine(target));
	}

	// Builds the action-bar line: "<name>: <stance>" with an Apex suffix when the
	// target qualifies for a capstone, mirroring the Combat HUD's enemy gem state.
	private static MutableComponent inspectLine(ServerPlayer target) {
		MutableComponent stance = stanceLabel(target);
		MutableComponent line = Component.translatable("inspect.attuned.line",
			target.getDisplayName(), stance);

		Optional<Apex.Capstone> capstone = Apex.capstoneOf(target);
		if (capstone.isPresent()) {
			boolean armed = Resonance.atApex(target);
			Component state = Component.translatable(armed
				? "inspect.attuned.apex.ready" : "inspect.attuned.apex.dormant");
			line.append(Component.translatable("inspect.attuned.apex",
				Component.literal(capstone.get().displayName()).withStyle(capstone.get().chatColor()),
				state).withStyle(ChatFormatting.GRAY));
		}
		return line;
	}

	// The target's stance word: Discord, a committed affinity, or Neutral.
	private static MutableComponent stanceLabel(ServerPlayer target) {
		if (Attunement.isDiscord(target)) {
			return Component.translatable("inspect.attuned.discord").withStyle(ChatFormatting.LIGHT_PURPLE);
		}
		Optional<Affinity> committed = Attunement.committedAffinity(target);
		if (committed.isEmpty()) {
			return Component.translatable("inspect.attuned.neutral").withStyle(ChatFormatting.GRAY);
		}
		Affinity affinity = committed.get();
		return Component.translatable("inspect.attuned.affinity." + affinity.name().toLowerCase(Locale.ROOT))
			.withStyle(affinityColor(affinity));
	}

	private static ChatFormatting affinityColor(Affinity affinity) {
		return switch (affinity) {
			case FURY -> ChatFormatting.RED;
			case BASTION -> ChatFormatting.GOLD;
			case ZEPHYR -> ChatFormatting.AQUA;
			case HOLY -> ChatFormatting.YELLOW;
			case TIDE -> ChatFormatting.BLUE;
			case FORGE -> ChatFormatting.DARK_RED;
			case VERDANT -> ChatFormatting.GREEN;
			case UMBRAL -> ChatFormatting.DARK_PURPLE;
		};
	}
}
