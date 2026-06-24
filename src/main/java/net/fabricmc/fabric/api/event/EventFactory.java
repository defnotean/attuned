package net.fabricmc.fabric.api.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
		private T invoker;

		private ArrayBackedEvent(Class<T> type, Function<T[], T> invokerFactory) {
			this.type = Objects.requireNonNull(type, "type");
			this.invokerFactory = Objects.requireNonNull(invokerFactory, "invokerFactory");
			refreshInvoker();
		}

		@Override
		public void register(T listener) {
			listeners.add(Objects.requireNonNull(listener, "listener"));
			refreshInvoker();
		}

		@Override
		public T invoker() {
			return invoker;
		}

		private void refreshInvoker() {
			invoker = invokerFactory.apply(listenerArray());
		}

		@SuppressWarnings("unchecked")
		private T[] listenerArray() {
			T[] array = (T[]) Array.newInstance(type, listeners.size());
			return listeners.toArray(array);
		}
	}
}
