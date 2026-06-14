package dev.attuned.client;

import dev.attuned.network.InspectRequestPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Crouch-and-look affinity inspect. While the local player sneaks and the
 * crosshair rests on another player, this watcher counts hover ticks; once the
 * hover holds for {@link #INSPECT_HOLD_TICKS} ticks it sends a single
 * {@link InspectRequestPayload} and waits — it never re-sends while the same
 * target stays under the crosshair, and the counter resets the moment the target
 * changes, the player stands up, or the crosshair leaves the target.
 *
 * <p>All the state revealed is server-resolved; the client only nominates which
 * entity it is looking at, so this cannot leak attunement data the server would
 * not already volunteer to an onlooker.</p>
 */
@Environment(EnvType.CLIENT)
public final class AffinityInspectClient {
	/** Sustained hover (in client ticks, ~1.5s at 20 TPS) before one request fires. */
	private static final int INSPECT_HOLD_TICKS = 30;

	private static int hoverTicks;
	private static int hoveredEntityId = -1;
	private static boolean initialized;

	private AffinityInspectClient() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(AffinityInspectClient::tick);
	}

	private static void tick(Minecraft client) {
		Player player = client.player;
		if (client.level == null || player == null || !player.isShiftKeyDown()) {
			reset();
			return;
		}

		// Reuse the Combat HUD's notion of "what the crosshair is on": the picked
		// entity must be another player to inspect, never a mob.
		Entity picked = client.crosshairPickEntity;
		if (!(picked instanceof Player target) || target == player || !target.isAlive()) {
			reset();
			return;
		}

		int targetId = target.getId();
		if (targetId != hoveredEntityId) {
			hoveredEntityId = targetId;
			hoverTicks = 0;
		}

		hoverTicks++;
		// Send exactly once when the accumulator first reaches the threshold; the
		// equality (not >=) guarantees one packet per sustained hover, no spam.
		if (hoverTicks == INSPECT_HOLD_TICKS) {
			ClientPlayNetworking.send(new InspectRequestPayload(targetId));
		}
	}

	private static void reset() {
		hoverTicks = 0;
		hoveredEntityId = -1;
	}
}
