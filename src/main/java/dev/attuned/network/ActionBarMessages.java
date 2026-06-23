package dev.attuned.network;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.compat.PlayerMessages;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared action-bar priority gate so routine feedback cannot bury urgent cues. */
public final class ActionBarMessages {
	private static final int HOLD_TICKS = 20;
	private static final Map<UUID, Decision> LAST_SENT = new HashMap<>();

	static {
		AttunedPlayerCleanup.onForget(LAST_SENT::remove);
		AttunedServerCleanup.onStop(LAST_SENT::clear);
	}

	private ActionBarMessages() {}

	public enum Priority {
		AMBIENT(0),
		PROGRESS(1),
		ABILITY(2),
		WARNING(3),
		CRITICAL(4);

		private final int rank;

		Priority(int rank) {
			this.rank = rank;
		}
	}

	public record Decision(Priority priority, long tick) {
		public Decision {
			priority = Objects.requireNonNull(priority, "priority");
		}
	}

	public static boolean shouldDisplay(Decision previous, Priority next, long nowTick, long holdTicks) {
		Objects.requireNonNull(next, "next");
		if (previous == null || holdTicks <= 0L || nowTick - previous.tick() >= holdTicks) {
			return true;
		}
		return next.rank > previous.priority().rank;
	}

	public static void send(ServerPlayer player, Priority priority, Component message) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(message, "message");
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		if (!shouldDisplay(LAST_SENT.get(id), priority, now, HOLD_TICKS)) {
			return;
		}
		LAST_SENT.put(id, new Decision(priority, now));
		PlayerMessages.overlay(player, message);
	}
}
