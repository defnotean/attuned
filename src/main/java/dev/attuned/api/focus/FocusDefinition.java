package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven definition of a Focus accessory — which item it is, its attunement
 * cost, its affinity, the modifiers it grants, and an optional code behaviour.
 * Loaded from datapack JSON at {@code data/<namespace>/attuned/focus/<name>.json}.
 * An empty {@code affinity} means the Focus is affinity-neutral.
 */
public record FocusDefinition(
		Holder<Item> item,
		int cost,
		boolean unique,
		Optional<Affinity> affinity,
		List<ModifierEntry> modifiers,
		Optional<Identifier> behavior) {

	public static final Codec<FocusDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(FocusDefinition::item),
		Codec.intRange(0, 64).optionalFieldOf("cost", 1).forGetter(FocusDefinition::cost),
		Codec.BOOL.optionalFieldOf("unique", false).forGetter(FocusDefinition::unique),
		Affinity.CODEC.optionalFieldOf("affinity").forGetter(FocusDefinition::affinity),
		ModifierEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(FocusDefinition::modifiers),
		Identifier.CODEC.optionalFieldOf("behavior").forGetter(FocusDefinition::behavior)
	).apply(instance, FocusDefinition::new));
}
