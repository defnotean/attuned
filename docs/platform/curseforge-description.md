# Attuned

Attuned is a Fabric accessory mod for Minecraft 26.2 built around deliberate Focus loadouts, visible counterplay, and progression through attunement.

Equip Foci into six inventory slots, manage your attunement capacity, and build around trade-offs instead of stacking every bonus at once. If your equipped Foci exceed your capacity, the lowest-priority Foci go dormant until the build fits again.

## Highlights

- Attuned 1.5.2 - Minecraft 26.2 Chaos Cubed moves the current release line to Minecraft 26.2 with Fabric Loader 0.19.3+, Fabric API 0.152.1+26.2, and the 1.5.1 Updraft Focus, smooth boost/brake controls, flight feedback, and PvP exhaustion safeguard.
- Deep Lanterns add four exploration-support Foci: Cavewick for lantern-marked cave routes, Glowline for same-dimension Circle pings, Rescueflame for drowning party members, and Depthglass for lodestone-compass navigation hints.
- Circles are temporary server-authoritative expedition parties with invite flows, party HUD state, pings, disconnect cleanup, and bounded shared contribution windows.
- Shared progress stays controlled: Circle credit requires same-dimension, nearby, recent contribution, so coordinated play feels better without passive proximity farming Pact, Field, Circle, surge, or party progress.
- Safer build sharing validates imported Reliquary build codes against the server Focus registry, slot data, names, setup metadata, warnings, and required Focus ids before saving or applying.
- Expanded datapack behavior palettes cover block-context effects, navigation hints, party assists, item-use windows, and marked-target patterns for pack authors.
- 99 Foci across mobility, defense, combat, stealth, holy, seafaring, shadow, rescue, utility, and the eightfold counter wheel.
- 16 new pure-affinity modifier Foci fill out the expanded roster with simple, readable stat identities and animated medallion art.
- Eight Affinity Pacts and expanded Apex capstones reward commitment to any single Affinity, while Discord and neutral builds keep their own endgame paths.
- Readable creative inventory tabs split Foci into Fury & Bastion, Zephyr & Holy, Tide & Forge, Verdant & Umbral, and Utility & Tools.
- Focus Confluences wake small set bonuses when specific active Focus combinations align, with journal discovery and HUD pips.
- Custom Focus visual motifs add subtle particle feedback to selected Foci without changing their balance identity.
- Focus Reliquary and Grand Focus Reliquary store spare Foci, save named builds, preview missing items, and apply loadouts from your bag/inventory.
- Data-driven authoring supports datapack Foci, behavior palettes, `/attuned validate`, a blank custom Focus item pool, and a worked example pack.

## How it plays

Attuned is about building a kit, not wearing a pile of stats. A Focus can be active, dormant, part of a Pact, part of a Confluence, tied to a faction, and part of an Affinity identity. Tooltips keep the Focus-specific readout clean, while the Attunement Journal explains the full matchup web.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.152.1+26.2

Lootr is optional but recommended for modpacks. Attuned injects Foci and shard fragments into vanilla loot tables rather than adding custom loot containers, so Lootr's per-player containers can roll the same rewards.

## Links

- Documentation and authoring guides are included in the repository under `docs/`.
- Current release notes explain the larger gameplay-polish patch, including the roughly 14k-line increase from Circles, Deep Lanterns, networking, behavior data, docs, gallery assets, and regression coverage.
