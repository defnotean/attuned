# Attuned

Attuned is a Fabric accessory mod for **Minecraft 26.1.2** built around deliberate Focus loadouts, visible counterplay, and progression through attunement.

Equip **Foci** into six inventory slots, manage your **attunement capacity**, and build around trade-offs instead of stacking every bonus at once. If your equipped Foci exceed your capacity, the lowest-priority Foci go dormant until the build fits again.

## What is in the current release?

- **78 Foci** across mobility, defense, combat, stealth, holy, seafaring, shadow, utility, and the new Aspect counter wheel.
- **Four core affinities** — Fury, Bastion, Zephyr, and Holy — form the original combat counter cycle, with Discord for risky mixed-affinity builds.
- **Aspect counter wheel** — Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, and Umbral each counter two Aspects and are countered by two others. Aspect-bearing Foci show their strengths and weaknesses in tooltips.
- **12 new Aspect Foci** across Tide, Forge, Verdant, and Umbral: Undertow, Riptide Heart, Pearlguard, Slagbrand, Anvilheart, Sparkweld, Thornwake, Seedcall, Bramblegate, Nullveil, Cinderthief, and Snaremoon.
- **Image-generation-derived Focus art** for the new Aspect Foci, processed into animated Minecraft item textures so the new icons match the existing medallion/talisman look.
- **Pacts and Apex capstones**, including Maelstrom for full Discord builds and Stillpoint for neutral builds.
- **Focus Reliquary and Grand Focus Reliquary** for storing spare Foci, saving loadouts, previewing missing items, and applying builds from your reliquary/inventory.
- **Focus Confluences** that wake small set bonuses from specific active Focus combinations, with HUD pips and journal discovery.
- **Focus Tempering** at the Altar of Reweaving: duplicate Foci can become stronger, costlier Tempered copies.
- **Attunement Sanctums** in-world and thunderstorm **Resonant Surges** for exploration and resonance events.
- **Datapack authoring support**: Focus definitions, behavior palettes, `/attuned validate`, blank custom Focus items, and an example pack.

## Design goal

Attuned should feel like choosing a build, not collecting free passive bonuses. Foci have costs, slot priority matters, dormancy prevents over-stacking, and the new Aspect system makes counterplay readable. A good build has an identity — and a weakness.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.2+
- Fabric API

Lootr is optional but recommended for modpacks. Attuned uses vanilla loot injection for Foci and shard fragments, so Lootr's per-player containers can still roll Attuned rewards.

## Documentation

The repository includes player-facing and authoring documentation under `docs/`, including Focus authoring, behavior palettes, the reference tables, and release notes generated from `CHANGELOG.md`.
