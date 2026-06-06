package dev.attuned.attunement;

import dev.attuned.api.focus.FocusDefinition;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;

/**
 * O(1) item-to-{@link FocusDefinition} lookup over the {@code FOCUS_DEFINITIONS}
 * datapack registry.
 *
 * <p>Scanning the whole registry on every query is wasteful once it is hit
 * several times per player per tick, so each registry is indexed once into an
 * item-keyed map. The index is keyed by the {@link Registry} instance itself:
 * datapack registries are frozen and immutable, so a registry reloaded into a
 * world — or a separate client-synced copy — is always a distinct instance and
 * gets its own index. Weak keys let the index for an unloaded world's registry
 * be collected with it.
 */
public final class FocusLookup {
	private FocusLookup() {}

	private static final Map<Registry<FocusDefinition>, Map<Item, FocusDefinition>> INDEX =
		Collections.synchronizedMap(new WeakHashMap<>());

	/** The definition registered for {@code item} in {@code registry}, if any. */
	public static Optional<FocusDefinition> forItem(Registry<FocusDefinition> registry, Item item) {
		Map<Item, FocusDefinition> byItem = INDEX.computeIfAbsent(registry, FocusLookup::index);
		return Optional.ofNullable(byItem.get(item));
	}

	private static Map<Item, FocusDefinition> index(Registry<FocusDefinition> registry) {
		Map<Item, FocusDefinition> byItem = new IdentityHashMap<>();
		for (FocusDefinition def : registry) {
			Item item = def.item().value();
			FocusDefinition previous = byItem.putIfAbsent(item, def);
			if (previous != null) {
				throw new IllegalStateException("Duplicate FocusDefinition item: " + item);
			}
		}
		return byItem;
	}
}
