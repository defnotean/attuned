package net.fabricmc.fabric.api.entity.event.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ServerEntityLevelChangeEvents {
	public static final AfterPlayerChangeLevel AFTER_PLAYER_CHANGE_LEVEL = new AfterPlayerChangeLevel();

	private ServerEntityLevelChangeEvents() {}

	public static final class AfterPlayerChangeLevel {
		private final List<Callback> callbacks = new ArrayList<>();

		private AfterPlayerChangeLevel() {
			NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
				if (!(event.getEntity() instanceof ServerPlayer player)) {
					return;
				}
				ServerLevel origin = player.level().getServer().getLevel(event.getFrom());
				ServerLevel destination = player.level().getServer().getLevel(event.getTo());
				if (origin == null || destination == null) {
					return;
				}
				for (Callback callback : List.copyOf(callbacks)) {
					callback.afterChangeLevel(player, origin, destination);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void afterChangeLevel(ServerPlayer player, ServerLevel origin, ServerLevel destination);
	}
}
