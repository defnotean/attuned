# Attuned

An accessory mod for **Minecraft 1.20.6** (Fabric).

Equip **Foci** into six inventory slots, but mind your **attunement capacity**. Go over budget and your lowest-priority Foci go dormant. Builds are deliberate, not stacked.

[Download on Modrinth](https://modrinth.com/mod/attuned-mod) | [Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/attuned)

## At a glance

- 95 Foci across mobility, defense, combat, stealth, holy, seafaring, shadow, utility, and the eightfold counter wheel
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

## Current release — Attuned 1.5.1

- **Updraft Focus** — a new elytra utility Focus that boosts forward while holding jump and brakes hard while holding sprint/control.
- **Smoother flight control** — Updraft thrust and braking ease velocity instead of snapping it, so long glides are calmer and easier to steer.
- **Flight feedback** — boost, brake, and exhaustion states use restrained vanilla particles, sounds, and action-bar messages.
- **PvP exhaustion safeguard** — sustained PvP pressure for more than five seconds makes Updraft falter briefly, applying a hard brake plus short Weakness and Slowness.
- **1.5.0 foundation** — Eightfold Affinities, Pact Trials, pact tacticals, Affinity Loom, build sharing, faction set bonuses, Resonant Surges, and expanded HUD/journal surfacing remain the core release base.

## Foundation — Resonant Depths

- The promoted **Tide, Forge, Verdant, and Umbral** affinities expand the counter wheel from four to eight while the original four-affinity Pact/Discord cycle survives as a subset.
- The eightfold roster includes 95 Foci across all affinity, faction, and utility lanes. Tooltips show each Focus's affinity; the Attunement Journal carries the matchup reference.
- The new affinity Focus textures ship as crisp animated item sheets with bold medallion silhouettes, disciplined affinity palettes, and inventory-scale readability checks.
- **Focus Confluences**, **Tempering**, **Grand Focus Reliquary**, **Attunement Sanctums**, **Resonant Surges**, datapack-defined Focus behavior palettes, and expanded journal/tooltips round out the release.

## Requirements

Minecraft 1.20.6, Fabric Loader 0.19.3+, Fabric API

Lootr is optional but suggested for modpacks. Attuned does not add custom
loot containers; it injects Foci and shard fragments into vanilla loot tables,
so Lootr's per-player containers can roll the same chest rewards. Wandering
traders can also rarely offer the journal or a shard fragment.

## Modding it

Foci, mob affinities, and tunables are data-driven. See [`docs/`](docs/).

## License

MIT. See [LICENSE](LICENSE).
