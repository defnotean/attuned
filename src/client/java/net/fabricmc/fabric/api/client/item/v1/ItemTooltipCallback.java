package net.fabricmc.fabric.api.client.item.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ItemTooltipCallback {
	public static final Event EVENT = new Event();

	private ItemTooltipCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void getTooltip(ItemStack stack, Object context, List<Component> lines);
	}
}
