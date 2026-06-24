package net.fabricmc.fabric.api.entity.event.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;

public final class ServerEntityWorldChangeEvents {
	public static final AfterPlayerChangeWorld AFTER_PLAYER_CHANGE_WORLD = new AfterPlayerChangeWorld();

	private ServerEntityWorldChangeEvents() {}

	public static final class AfterPlayerChangeWorld {
		private final List<Callback> callbacks = new ArrayList<>();

		private AfterPlayerChangeWorld() {
			MinecraftForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent event) -> {
				if (!(event.getEntity() instanceof ServerPlayer player)) {
					return;
				}
				ServerLevel origin = player.getLevel();
				ServerLevel destination = player.server.getLevel(event.getDimension());
				if (destination == null) {
					return;
				}
				for (Callback callback : List.copyOf(callbacks)) {
					callback.afterChangeWorld(player, origin, destination);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void afterChangeWorld(ServerPlayer player, ServerLevel origin, ServerLevel destination);
	}
}
