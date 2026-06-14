package dev.attuned.api.synergy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.attuned.api.focus.ModifierEntry;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Data-driven definition of a Confluence — the member Foci that must all be active,
 * the modifiers granted while active, and an optional code behaviour.
 * Loaded from datapack JSON at {@code data/<namespace>/attuned/synergy/<name>.json}.
 * A Confluence costs no attunement budget; it is an emergent reward for an
 * already-paid-for build (see the Focus Confluences design).
 */
public record SynergyDefinition(
        List<Identifier> members,
        List<ModifierEntry> modifiers,
        Optional<Identifier> behavior) {

    public SynergyDefinition {
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        behavior = Objects.requireNonNull(behavior, "behavior");
    }

    public static final Codec<SynergyDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.listOf().fieldOf("members").forGetter(SynergyDefinition::members),
        ModifierEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(SynergyDefinition::modifiers),
        Identifier.CODEC.optionalFieldOf("behavior").forGetter(SynergyDefinition::behavior)
    ).apply(instance, SynergyDefinition::new));
}
