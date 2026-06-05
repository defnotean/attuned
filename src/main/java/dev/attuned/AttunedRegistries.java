package dev.attuned;

import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Registry keys and code-behaviour lookups for Attuned.
 */
public final class AttunedRegistries {
	private AttunedRegistries() {}

	/** Datapack registry of Focus definitions ({@code data/<ns>/attuned/focus/<name>.json}). */
	public static final ResourceKey<Registry<FocusDefinition>> FOCUS_DEFINITIONS =
		ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "focus"));

	private static final Map<Identifier, FocusBehavior> BEHAVIORS = new HashMap<>();

	/** Registers a code behaviour under an id that a {@link FocusDefinition} can reference. */
	public static void registerBehavior(Identifier id, FocusBehavior behavior) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(behavior, "behavior");
		FocusBehavior previous = BEHAVIORS.putIfAbsent(id, behavior);
		if (previous != null) {
			throw new IllegalStateException("Duplicate Focus behavior id: " + id);
		}
	}

	/** Returns the behaviour registered under the given id, or {@code null} if none. */
	public static FocusBehavior getBehavior(Identifier id) {
		return BEHAVIORS.get(id);
	}
}
