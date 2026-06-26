package dev.attuned.platform;

import java.util.Objects;
import net.neoforged.bus.api.IEventBus;

public final class NeoForgeEventBuses {
	private static IEventBus modEventBus;

	private NeoForgeEventBuses() {}

	public static synchronized void setModEventBus(IEventBus eventBus) {
		modEventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	public static synchronized IEventBus modEventBus() {
		if (modEventBus == null) {
			throw new IllegalStateException("NeoForge mod event bus has not been initialized");
		}
		return modEventBus;
	}
}
