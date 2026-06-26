# Attuned

An accessory mod for **Minecraft 26.2**. The current published artifact is
Fabric; Quilt, NeoForge, and Forge support are tracked as separate validation or
port branches and must have their own compatibility evidence or dedicated
artifacts before they are listed as supported.

Equip **Foci** into six inventory slots, but mind your **attunement capacity**. Go over budget and your lowest-priority Foci go dormant. Builds are deliberate, not stacked.

[Download on Modrinth](https://modrinth.com/mod/attuned-mod) | [Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/attuned)

## At a glance

- 99 Foci across mobility, defense, combat, stealth, holy, seafaring, shadow, rescue, utility, and the eightfold counter wheel
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

## Current release — Attuned 1.5.2

- **Minecraft 26.2 Chaos Cubed support** — the `latest` line now targets Minecraft 26.2 with Fabric Loader 0.19.3+, Fabric API 0.152.1+26.2, Loom 1.17.11, and Gradle 9.5.1. Quilt, NeoForge, and Forge requirements are tracked separately until their compatibility evidence or dedicated artifacts exist.
- **26.2 runtime compatibility** — entity/knockback APIs, client render submit nodes, and screen/toast hooks have been updated so dedicated server and client runtimes launch cleanly.
- **Preserved 26.1.2 line** — Minecraft 26.1.2 remains available on `maintenance/minecraft-26.1.2` while `latest` moves forward.
- **Updraft Focus carried forward** — the 1.5.1 elytra utility Focus, smoother boost/brake controls, flight feedback, and PvP exhaustion safeguard are included.
- **1.5.0 foundation** — Eightfold Affinities, Pact Trials, pact tacticals, Affinity Loom, build sharing, faction set bonuses, Resonant Surges, and expanded HUD/journal surfacing remain the core release base.

## Foundation — Resonant Depths

- The promoted **Tide, Forge, Verdant, and Umbral** affinities expand the counter wheel from four to eight while the original four-affinity Pact/Discord cycle survives as a subset.
- The eightfold roster includes 99 Foci across all affinity, faction, and utility lanes. Tooltips show each Focus's affinity; the Attunement Journal carries the matchup reference.
- The new affinity Focus textures ship as crisp animated item sheets with bold medallion silhouettes, disciplined affinity palettes, and inventory-scale readability checks.
- **Focus Confluences**, **Tempering**, **Grand Focus Reliquary**, **Attunement Sanctums**, **Resonant Surges**, datapack-defined Focus behavior palettes, and expanded journal/tooltips round out the release.

## Loader support and requirements

- Current Fabric release: Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API 0.152.1+26.2.
- Quilt: `quilt/1.19.2` is a native branch build/server-smoke candidate with Quilt Loom, Quilt Loader metadata, `quilt.mod.json`, Quilt entrypoint adapters, an Attuned-owned access widener, and Fabric API compatibility supplied through Quilt Loader. Release still requires Quilt client smoke plus hands-on combat HUD resonance-fill verification.
- NeoForge: `neoforge/1.21.1` and `neoforge/1.20.6` are dedicated branch build/server-smoke candidates with NeoForge metadata, entrypoint/event shims, deferred content registration, persistent player-state bridges, payload registration, and client HUD/key/screen hooks compiling and building. Headless NeoForge servers reach running worlds; release still requires client smoke plus hands-on combat HUD resonance-fill verification.
- Forge: branch build candidates exist for 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.6, 1.21.1, 1.21.11, 26.1.2, and 26.2 after the resonance HUD, owner-state sync, and modern persistence/reconnect passes. Do not install the Fabric jar in Forge; release still requires loader-specific artifacts, metadata, server/client smoke, and hands-on combat HUD smoke.

See [`docs/loader-support.md`](docs/loader-support.md) for the detailed Fabric
implementation notes and Quilt/Forge-family port plan.

Lootr is optional but suggested for modpacks. Attuned does not add custom
loot containers; it injects Foci and shard fragments into vanilla loot tables,
so Lootr's per-player containers can roll the same chest rewards. Wandering
traders can also rarely offer the journal or a shard fragment.

## Modding it

Foci, mob affinities, and tunables are data-driven. See [`docs/`](docs/).

## License

MIT. See [LICENSE](LICENSE).
