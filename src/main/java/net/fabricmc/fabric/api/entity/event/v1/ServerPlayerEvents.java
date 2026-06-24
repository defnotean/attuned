package net.fabricmc.fabric.api.entity.event.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class ServerPlayerEvents {
	public static final AfterRespawn AFTER_RESPAWN = new AfterRespawn();

	private ServerPlayerEvents() {}

	public static final class AfterRespawn {
		private final List<Callback> callbacks = new ArrayList<>();

		private AfterRespawn() {
			MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
				if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)
						|| !(event.getEntity() instanceof ServerPlayer newPlayer)) {
					return;
				}
				boolean alive = !event.isWasDeath();
				for (Callback callback : List.copyOf(callbacks)) {
					callback.afterRespawn(oldPlayer, newPlayer, alive);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
	}
}
