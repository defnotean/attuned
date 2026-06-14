# Attuned

An accessory mod for **Minecraft 26.1.2** (Fabric).

Equip **Foci** into six inventory slots, but mind your **attunement capacity**. Go over budget and your lowest-priority Foci go dormant. Builds are deliberate, not stacked.

[Download on Modrinth](https://modrinth.com/mod/attuned-mod) | [Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/attuned)

## At a glance

- 78 Foci across mobility, defense, combat, stealth, holy, seafaring, shadow, utility, and the new Aspect counter wheel
- **New in 1.4.0: Tide, Forge, Verdant, and Umbral Aspect Foci** — Undertow, Riptide Heart, Pearlguard, Slagbrand, Anvilheart, Sparkweld, Thornwake, Seedcall, Bramblegate, Nullveil, Cinderthief, and Snaremoon
- **Refreshed original Focus art** for the new Aspect Foci, processed into animated Minecraft item sheets so they sit with the existing medallion/talisman theme
- **The Unseen**: a stealth faction built around quiet movement, low-light veils, smoke misdirection, and ambush openings
- Four **affinities** (Fury, Bastion, Zephyr, Holy) in a counter-combat cycle, plus the cross-affinity **Discord** stance
- **Aspects** add the broader Wheel of Refusals: Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, and Umbral appear on Foci, with the full matchup reference kept in the Attunement Journal
- **Pacts**: set bonuses that wake when you commit (Pyresworn, Stoneheart, Windrunner, Untethered)
- **Apex capstones** at near-full commitment, including Maelstrom for Discord and Stillpoint for neutral builds, gated by combat **Resonance**
- **Attunement Altar**: bind shards to grow capacity; glows with your stance
- Custom pixel-art UI for the Altar, Focus panel, combat HUD, and Attunement Journal
- Lootr-friendly survival loot: every Focus and shard fragment rolls through vanilla structures, fishing treasure, archaeology, and trial rewards; wandering traders can rarely offer the journal or a shard fragment
- Combat HUD shows your gem, your target's gem, and resonance at a glance

## Current patch — Attuned 1.4.1

- Focus descriptions/tooltips now show only each Focus's own effect plus Aspect identity.
- The **Attunement Journal** owns the full Aspect matchup reference, keeping the who-counters-who details in one in-game place.

## New in Attuned 1.4.0 — Resonant Depths

- The **Aspect counter wheel** adds a visible second layer of counterplay without breaking the original four-affinity Pact/Discord system.
- The first Aspect batch adds 12 Foci across **Tide**, **Forge**, **Verdant**, and **Umbral**. Tooltips show each Focus's Aspect; the Attunement Journal carries the matchup reference.
- The new Aspect Focus textures ship as crisp animated item sheets with bold medallion silhouettes, disciplined Aspect palettes, and inventory-scale readability checks.
- **Focus Confluences**, **Tempering**, **Grand Focus Reliquary**, **Attunement Sanctums**, **Resonant Surges**, datapack-defined Focus behavior palettes, and expanded journal/tooltips round out the release.

## Requirements

Minecraft 26.1.2, Fabric Loader 0.19.2+, Fabric API

Lootr is optional but suggested for modpacks. Attuned does not add custom
loot containers; it injects Foci and shard fragments into vanilla loot tables,
so Lootr's per-player containers can roll the same chest rewards. Wandering
traders can also rarely offer the journal or a shard fragment.

## Modding it

Foci, mob affinities, and tunables are data-driven. See [`docs/`](docs/).

## License

MIT. See [LICENSE](LICENSE).
