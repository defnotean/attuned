package net.fabricmc.fabric.api.command.v1;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;

public final class CommandRegistrationCallback {
	public static final Event EVENT = new Event();

	private CommandRegistrationCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}

		public void fire(CommandDispatcher<CommandSourceStack> dispatcher, boolean dedicated) {
			for (Callback callback : List.copyOf(callbacks)) {
				callback.register(dispatcher, dedicated);
			}
		}
	}

	@FunctionalInterface
	public interface Callback {
		void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean dedicated);
	}
}
