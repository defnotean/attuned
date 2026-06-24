package net.fabricmc.fabric.api.event.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class UseItemCallback {
	public static final Event EVENT = new Event();

	private UseItemCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {
			MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
				for (Callback callback : List.copyOf(callbacks)) {
					InteractionResultHolder<ItemStack> result = callback.interact(
						event.getEntity(), event.getEntity().level(), event.getHand());
					if (result.getResult().consumesAction()) {
						event.setCancellationResult(result.getResult());
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
		InteractionResultHolder<ItemStack> interact(Player player, Level level, InteractionHand hand);
	}
}
