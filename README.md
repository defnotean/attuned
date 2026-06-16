# Attuned

An accessory mod for **Minecraft 26.1.2** (Fabric).

Equip **Foci** into six inventory slots, but mind your **attunement capacity**. Go over budget and your lowest-priority Foci go dormant. Builds are deliberate, not stacked.

[Download on Modrinth](https://modrinth.com/mod/attuned-mod) | [Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/attuned)

## At a glance

- 94 Foci across mobility, defense, combat, stealth, holy, seafaring, shadow, utility, and the eightfold counter wheel
- **Eightfold Affinities** — Tide, Forge, Verdant, and Umbral now stand beside Fury, Bastion, Zephyr, and Holy as first-class lanes
- **Refreshed original Focus art** for the new affinity Foci, processed into animated Minecraft item sheets so they sit with the existing medallion/talisman theme
- **The Unseen**: a stealth faction built around quiet movement, low-light veils, smoke misdirection, and ambush openings
- Eight **affinities** (Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, Umbral) on one counter wheel — each beats two and is countered by two — plus the cross-affinity **Discord** stance. The Attunement Journal carries the full matchup reference.
- The promoted **Tide, Forge, Verdant, and Umbral** affinities round out the Wheel of Refusals; the older four-affinity cycle survives as a subset of the expanded matrix
- **Pacts**: set bonuses that wake when you commit three Foci to one affinity — Pyresworn, Stoneheart, Windrunner, Radiant Covenant, Tidesworn, Forgebound, Wildroot, Nightsworn — plus the mixed-spread Untethered
- **Apex capstones** at near-full commitment: Execute, Unyielding, Untouchable, Judgment, Riptide, Crucible, Bloomward, and Gloaming for committed lanes, plus Maelstrom for Discord and Stillpoint for neutral builds, all gated by combat **Resonance**
- **Attunement Altar**: bind shards to grow capacity; glows with your stance
- Custom pixel-art UI for the Altar, Focus panel, combat HUD, and Attunement Journal
- Lootr-friendly survival loot: every Focus and shard fragment rolls through vanilla structures, fishing treasure, archaeology, and trial rewards; wandering traders can rarely offer the journal or a shard fragment
- Combat HUD shows your gem, your target's gem, and resonance at a glance

## Current release — Attuned 1.5.0

- **Combat config tuning** — Discord damage multiplier and resonance gain/decay are server-configurable (`config/attuned.json`).
- **Pact tacticals** on the Focus Ability key (**R** by default), plus new ability Foci that claim the keybind.
- **Pact Trials (Tier 4)** — long-term goals per pact; permanent completion unlocks a small passive while that pact is awake again.
- **Faction set bonuses** for Tideborn, Forgebound, Wildroot, and Umbral (three+ active Foci sharing the faction).
- **Resonant surge interactivity** — server-wide surge fields grant amplified resonance; tunable interval, duration, and radius.
- **Onboarding hints** — one-shot toasts for first shard, dormant Focus, confluence, pact trial completion, and more.
- **Affinity Loom** at the Altar of Reweaving — reroll one Focus within its affinity for escalating Attunement Shards.
- **Build sharing** — Reliquary Share/Import copies `attuned:v1:` clipboard presets between players.
- **HUD and journal surfacing** — pact trial progress, Affinity Loom hints, and expanded rules in the Attunement Journal.

## Foundation — Resonant Depths

- The promoted **Tide, Forge, Verdant, and Umbral** affinities expand the counter wheel from four to eight while the original four-affinity Pact/Discord cycle survives as a subset.
- The eightfold roster includes 94 Foci across all affinity, faction, and utility lanes. Tooltips show each Focus's affinity; the Attunement Journal carries the matchup reference.
- The new affinity Focus textures ship as crisp animated item sheets with bold medallion silhouettes, disciplined affinity palettes, and inventory-scale readability checks.
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
