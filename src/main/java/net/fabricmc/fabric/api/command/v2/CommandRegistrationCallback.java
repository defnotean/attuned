package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class CommandRegistrationCallback {
	public static final Event EVENT = new Event();

	private CommandRegistrationCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}

		public void fire(CommandDispatcher<CommandSourceStack> dispatcher,
				CommandBuildContext registryAccess, Commands.CommandSelection environment) {
			for (Callback callback : List.copyOf(callbacks)) {
				callback.register(dispatcher, registryAccess, environment);
			}
		}
	}

	@FunctionalInterface
	public interface Callback {
		void register(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess, Commands.CommandSelection environment);
	}
}
