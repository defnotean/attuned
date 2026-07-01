package dev.attuned;

import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusBehaviorDef;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.synergy.SynergyDefinition;
import dev.attuned.content.behavior.DataFocusBehaviors;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry keys and the single code-first-then-data Focus behaviour funnel for Attuned.
 */
public final class AttunedRegistries {
	private AttunedRegistries() {}

	/** Datapack registry of Focus definitions ({@code data/<ns>/attuned/focus/<name>.json}). */
	public static final ResourceKey<Registry<FocusDefinition>> FOCUS_DEFINITIONS =
		ResourceKey.createRegistryKey(new ResourceLocation(Attuned.MOD_ID, "focus"));

	/** Datapack registry of Confluence definitions ({@code data/<ns>/attuned/synergy/<name>.json}). */
	public static final ResourceKey<Registry<SynergyDefinition>> SYNERGY_DEFINITIONS =
		ResourceKey.createRegistryKey(new ResourceLocation(Attuned.MOD_ID, "synergy"));
	/**
	 * Synced datapack registry of parameterized behaviour palette instances
	 * ({@code data/<ns>/attuned/focus_behavior/<id>.json}). A Focus's {@code behavior} id is
	 * resolved code-first, then falls back to building a runtime behaviour from this registry.
	 */
	public static final ResourceKey<Registry<FocusBehaviorDef>> FOCUS_BEHAVIORS =
		ResourceKey.createRegistryKey(new ResourceLocation(Attuned.MOD_ID, "focus_behavior"));

	private static final Map<ResourceLocation, FocusBehavior> BEHAVIORS = new HashMap<>();

	/** Per-RegistryAccess cache of behaviours built from data palette entries, so ticking never rebuilds. */
	private static final Map<RegistryAccess, Map<ResourceLocation, FocusBehavior>> DATA_BEHAVIOR_CACHE =
		new WeakHashMap<>();

	static {
		AttunedServerCleanup.onStop(AttunedRegistries::clearDataBehaviorCache);
	}

	/** Registers a code behaviour under an id that a {@link FocusDefinition} can reference. */
	public static void registerBehavior(ResourceLocation id, FocusBehavior behavior) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(behavior, "behavior");
		FocusBehavior previous = BEHAVIORS.putIfAbsent(id, behavior);
		if (previous != null) {
			throw new IllegalStateException("Duplicate Focus behavior id: " + id);
		}
	}

	/**
	 * Returns the code behaviour registered under the given id, or {@code null} if none.
	 * Code-only lookup; for full code-first-then-data resolution use
	 * {@link #getBehavior(ResourceLocation, RegistryAccess)}.
	 */
	public static FocusBehavior getBehavior(ResourceLocation id) {
		return BEHAVIORS.get(id);
	}

	/**
	 * Resolves a Focus behaviour code-first, then data: returns the registered code behaviour
	 * for {@code id} if one exists, otherwise builds (and caches) a runtime behaviour from the
	 * {@code focus_behavior} palette registry. Returns {@code null} if neither has it.
	 */
	public static FocusBehavior getBehavior(ResourceLocation id, RegistryAccess registries) {
		FocusBehavior code = BEHAVIORS.get(id);
		if (code != null) {
			return code;
		}
		if (registries == null) {
			return null;
		}
		return dataBehavior(id, registries);
	}

	private static synchronized FocusBehavior dataBehavior(ResourceLocation id, RegistryAccess registries) {
		Map<ResourceLocation, FocusBehavior> perAccess =
			DATA_BEHAVIOR_CACHE.computeIfAbsent(registries, key -> new HashMap<>());
		if (perAccess.containsKey(id)) {
			return perAccess.get(id);
		}
		FocusBehaviorDef def = registries.registryOrThrow(FOCUS_BEHAVIORS).get(id);
		if (def == null) {
			// Cache the miss: without this, every tick of a focus with a dangling
			// behavior id re-enters this synchronized method and re-queries the
			// registry. Warn once so pack authors can spot the typo.
			perAccess.put(id, null);
			Attuned.LOGGER.warn(
				"Attuned focus behavior '{}' is not registered in code or the focus_behavior registry; "
					+ "the focus will have no behavior until the id is fixed.", id);
			return null;
		}
		FocusBehavior built = DataFocusBehaviors.build(id, def);
		perAccess.put(id, built);
		return built;
	}

	private static synchronized void clearDataBehaviorCache() {
		DATA_BEHAVIOR_CACHE.clear();
	}
}
