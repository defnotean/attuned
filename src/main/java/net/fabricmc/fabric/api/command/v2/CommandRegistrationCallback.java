package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class CommandRegistrationCallback {
	public static final Event EVENT = new Event();

	private CommandRegistrationCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		private Event() {
			RegisterCommandsEvent.BUS.addListener(event -> {
				for (Callback callback : List.copyOf(callbacks)) {
					callback.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void register(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess, Commands.CommandSelection environment);
	}
}
