package dev.attuned.menu;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.content.AttunedContent;
import dev.attuned.content.AttunementAltarBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Networking for the Altar GUI: the Bind button's client-to-server payload
 * and its server-side receiver. Lives alongside the menu so the orchestrator
 * only has to call a single {@link #init()} from {@code Attuned.onInitialize}.
 */
public final class AltarNetworking {
	private AltarNetworking() {}

	private static boolean initialized;

	/**
	 * Minimum number of server ticks between two accepted binds from the same
	 * player. A short window — enough to absorb a forged-client spam burst
	 * without being noticeable to a human clicking the Bind button.
	 */
	private static final int BIND_COOLDOWN_TICKS = 5;

	/**
	 * Server-tick timestamp of each player's most recently accepted bind. Read
	 * and written only on the server thread inside the menu's access lambda.
	 * Entries are dropped on disconnect via {@link AttunedPlayerCleanup}.
	 */
	private static final Map<UUID, Long> LAST_BIND_TICK = new HashMap<>();

	/** Registers the bind payload type and its server-side receiver. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerPlayNetworking.registerGlobalReceiver(BindShardPayload.TYPE, (server, player, handler, buf, sender) -> {
			BindShardPayload payload = BindShardPayload.TYPE.read(buf);
			server.execute(() -> tryBind(player));
		});
		AttunedPlayerCleanup.onForget(LAST_BIND_TICK::remove);
		AttunedServerCleanup.onStop(LAST_BIND_TICK::clear);
	}

	/**
	 * Validates that the player has the Altar menu open with a shard in the
	 * input slot, then performs one bind: consumes a shard and raises capacity
	 * by the configured amount. Reachability is rechecked through the menu's
	 * {@code ContainerLevelAccess} so a forged payload from a fleeing player —
	 * or one who has dimension-changed since opening the GUI — cannot bind
	 * against a stale or wrong-dimension position.
	 */
	private static void tryBind(ServerPlayer player) {
		if (!(player.containerMenu instanceof AltarMenu menu)) {
			return;
		}
		menu.access().execute((level, pos) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return;
			}
			if (player.getLevel() != serverLevel) {
				return;
			}
			BlockState state = serverLevel.getBlockState(pos);
			if (!state.is(AttunedContent.ATTUNEMENT_ALTAR)) {
				return;
			}
			if (player.distanceToSqr(
					pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 16.0D) {
				return;
			}
			Container input = menu.inputContainer();
			ItemStack shard = input.getItem(AltarMenu.INPUT_SLOT);
			if (shard.isEmpty() || !shard.is(AttunedContent.ATTUNEMENT_SHARD)) {
				return;
			}
			// Silently ignore binds once capacity is at the cap. The client-side
			// Bind button is already disabled in this state, so a request reaching
			// us here is a forged or out-of-sync client; bindShard's "already at
			// its fullest" chat message is fine for a real right-click bind but
			// would be a 20Hz spam vector if echoed for every payload.
			if (AttunedAttachments.getCapacity(player) >= AttunedConfig.get().capacityCap()) {
				return;
			}
			// Per-player rate limit: a successful bind locks out further binds
			// from the same player for BIND_COOLDOWN_TICKS, defense in depth
			// against a forged client that bypasses the button-disable above.
			UUID id = player.getUUID();
			long now = serverLevel.getGameTime();
			Long last = LAST_BIND_TICK.get(id);
			if (last != null && now - last < BIND_COOLDOWN_TICKS) {
				return;
			}
			// bindShard mutates the stack in place — it shrinks by one on success.
			AttunementAltarBlock.bindShard(serverLevel, pos, state, player, shard);
			LAST_BIND_TICK.put(id, now);
			// Whether or not it bound (cap reached, etc.), make sure the slot reflects
			// the current stack — including the case where bindShard emptied it.
			if (shard.isEmpty()) {
				input.setItem(AltarMenu.INPUT_SLOT, ItemStack.EMPTY);
			} else {
				input.setItem(AltarMenu.INPUT_SLOT, shard);
			}
			menu.broadcastChanges();
		});
	}
}
