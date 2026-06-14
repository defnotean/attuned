package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Data-driven definition of a Focus accessory — which item it is, its attunement
 * cost, its affinity/aspect metadata, the modifiers it grants, and an optional code behaviour.
 * Loaded from datapack JSON at {@code data/<namespace>/attuned/focus/<name>.json}.
 * An empty {@code affinity} means the Focus is affinity-neutral.
 * An empty {@code aspect} means the Focus has not opted into the expanded counter wheel.
 * An empty {@code faction} means the Focus is not part of a named content family.
 */
public record FocusDefinition(
		Holder<Item> item,
		int cost,
		boolean unique,
		Optional<Affinity> affinity,
		Optional<Aspect> aspect,
		Optional<Identifier> faction,
		List<ModifierEntry> modifiers,
		Optional<Identifier> behavior) {

	private static final int MIN_COST = 0;
	private static final int MAX_COST = 64;

	public FocusDefinition {
		item = Objects.requireNonNull(item, "item");
		if (cost < MIN_COST || cost > MAX_COST) {
			throw new IllegalArgumentException("Focus cost must be between " + MIN_COST + " and " + MAX_COST);
		}
		affinity = Objects.requireNonNull(affinity, "affinity");
		aspect = Objects.requireNonNull(aspect, "aspect");
		faction = Objects.requireNonNull(faction, "faction");
		modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
		behavior = Objects.requireNonNull(behavior, "behavior");
	}

	public static final Codec<FocusDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(FocusDefinition::item),
		Codec.intRange(MIN_COST, MAX_COST).optionalFieldOf("cost", 1).forGetter(FocusDefinition::cost),
		Codec.BOOL.optionalFieldOf("unique", false).forGetter(FocusDefinition::unique),
		Affinity.CODEC.optionalFieldOf("affinity").forGetter(FocusDefinition::affinity),
		Aspect.CODEC.optionalFieldOf("aspect").forGetter(FocusDefinition::aspect),
		Identifier.CODEC.optionalFieldOf("faction").forGetter(FocusDefinition::faction),
		ModifierEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(FocusDefinition::modifiers),
		Identifier.CODEC.optionalFieldOf("behavior").forGetter(FocusDefinition::behavior)
	).apply(instance, FocusDefinition::new));
}
