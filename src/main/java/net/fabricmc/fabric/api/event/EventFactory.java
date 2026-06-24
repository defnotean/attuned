package net.fabricmc.fabric.api.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EventFactory {
	private EventFactory() {}

	public static <T> Event<T> createArrayBacked(Class<T> type, Function<T[], T> invokerFactory) {
		return new ArrayBackedEvent<>(type, invokerFactory);
	}

	private static final class ArrayBackedEvent<T> implements Event<T> {
		private final Class<T> type;
		private final Function<T[], T> invokerFactory;
		private final List<T> listeners = new ArrayList<>();

		private ArrayBackedEvent(Class<T> type, Function<T[], T> invokerFactory) {
			this.type = type;
			this.invokerFactory = invokerFactory;
		}

		@Override
		public void register(T listener) {
			listeners.add(listener);
		}

		@Override
		public T invoker() {
			@SuppressWarnings("unchecked")
			T[] callbacks = listeners.toArray(size -> (T[]) Array.newInstance(type, size));
			return invokerFactory.apply(callbacks);
		}
	}
}
