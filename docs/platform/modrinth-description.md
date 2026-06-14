# Attuned

Attuned is a Fabric accessory mod for **Minecraft 26.1.2** built around deliberate Focus loadouts, visible counterplay, and progression through attunement.

Equip **Foci** into six inventory slots, manage your **attunement capacity**, and build around trade-offs instead of stacking every bonus at once. If your equipped Foci exceed your capacity, the lowest-priority Foci go dormant until the build fits again.

## Highlights

- **Attuned 1.4.1 patch** — Focus descriptions/tooltips now show only Focus-specific effects plus Aspect identity; the Attunement Journal owns the full Aspect matchup reference.
- **78 Foci** across mobility, defense, combat, stealth, holy, seafaring, shadow, utility, and the new Aspect counter wheel.
- **Four core affinities** — Fury, Bastion, Zephyr, and Holy — form the original combat counter cycle, with Discord for risky mixed-affinity builds.
- **New Aspect counter wheel** — Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, and Umbral appear as broader Focus identities. Individual Focus descriptions show only the Aspect name; the Attunement Journal carries the matchup reference.
- **12 new Aspect Foci** in the first Tide / Forge / Verdant / Umbral batch: Undertow, Riptide Heart, Pearlguard, Slagbrand, Anvilheart, Sparkweld, Thornwake, Seedcall, Bramblegate, Nullveil, Cinderthief, and Snaremoon.
- **Refreshed original Focus art** for the new Aspect Foci, imported into animated 64x512 Minecraft item sheets so the new icons fit the existing medallion/talisman theme.
- **Pacts and Apex capstones** reward commitment, including Maelstrom for full Discord builds and Stillpoint for neutral builds.
- **Focus Reliquary and Grand Focus Reliquary** store spare Foci, show equipped slots, save named builds, preview missing items, and apply loadouts from your bag/inventory.
- **Focus Confluences** wake small set bonuses when specific Focus combinations are active, with journal discovery and HUD pips.
- **Focus Tempering** lets the Altar of Reweaving fuse duplicate Foci into stronger, costlier Tempered copies.
- **Attunement Sanctums and Resonant Surges** add world discovery and thunderstorm resonance events.
- **Data-driven authoring** supports datapack Foci, behavior palettes, `/attuned validate`, a blank custom Focus item pool, and a worked example pack.

## How it plays

Attuned is about building a kit, not wearing a pile of stats. A Focus can be active, dormant, part of a Pact, part of a Confluence, tied to a faction, and now part of an Aspect identity. Tooltips keep the Focus-specific readout clean, while the Attunement Journal explains the matchup web.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.2+
- Fabric API

Lootr is optional but recommended for modpacks. Attuned injects Foci and shard fragments into vanilla loot tables rather than adding custom loot containers, so Lootr's per-player containers can roll the same rewards.

## Links

- Documentation and authoring guides are included in the repository under `docs/`.
- Current release notes are generated from the matching `CHANGELOG.md` section for the uploaded version.
