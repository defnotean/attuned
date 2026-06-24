package net.fabricmc.fabric.api.event.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class UseBlockCallback {
	public static final Event EVENT = new Event();

	private UseBlockCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {
			MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
				for (Callback callback : List.copyOf(callbacks)) {
					InteractionResult result = callback.interact(event.getEntity(),
						event.getEntity().getLevel(), event.getHand(), event.getHitVec());
					if (result.consumesAction()) {
						event.setCancellationResult(result);
						event.setCanceled(true);
						return;
					}
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		InteractionResult interact(Player player, Level level,
			InteractionHand hand, BlockHitResult hitResult);
	}
}
