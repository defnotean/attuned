package dev.attuned.network;

/** Common registration for Attuned server-to-client payloads. */
public final class JournalNetworking {
	private static boolean initialized;

	private JournalNetworking() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
	}
}
