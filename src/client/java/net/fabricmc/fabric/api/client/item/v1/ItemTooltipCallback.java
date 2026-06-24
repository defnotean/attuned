package net.fabricmc.fabric.api.client.item.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public final class ItemTooltipCallback {
	public static final Event EVENT = new Event();

	private ItemTooltipCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {
			MinecraftForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> {
				Context context = new Context(event.getEntity());
				for (Callback callback : List.copyOf(callbacks)) {
					callback.getTooltip(event.getItemStack(), context, event.getFlags(), event.getToolTip());
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void getTooltip(ItemStack stack, Context context, TooltipFlag type, List<Component> lines);
	}

	public record Context(net.minecraft.world.entity.player.Player player) {
	}
}
