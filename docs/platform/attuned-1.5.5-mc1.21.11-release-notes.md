## Attuned 1.5.5+mc1.21.11 - Gameplay Polish Backport

### Added
- **Deep Lanterns faction** - four exploration-support Foci bring the shipped roster to 99 Foci and give cave expeditions a support identity that is not another raw damage lane.
  - **Cavewick Focus** rewards player-built routes: placed lanterns and soul lanterns in a small radius keep gentle Night Vision refreshed.
  - **Glowline Focus** points toward recent same-dimension Circle pings that already passed server membership and target checks.
  - **Rescueflame Focus** periodically assists a nearby drowning Circle member with Water Breathing on a Circle-level cooldown.
  - **Depthglass Focus** reads a held lodestone compass target and gives restrained same-dimension navigation hints.
- **Server-authoritative Circles** - a temporary expedition party system with create, invite, accept, leave, disband, and kick commands under `/attuned circle ...`. The server owns membership, invite expiry, capacity, cooldowns, disconnect cleanup, and snapshot syncing.
- **Party HUD and invite prompts** - clients receive Circle snapshots, invite prompts, and recent ping notices through dedicated payloads. The HUD shows public member summaries, role labels, invite expiry, and ping locations without item sharing or hidden inventory exposure.
- **Circle pings and shared contribution windows** - pings become server-accepted navigation targets, and shared progress requires nearby, online, same-dimension, recent contribution. Passive proximity alone should not farm Pact, Field, Circle, surge, or party progress.
- **Party-aware Pact Trial support** - eligible Circle contributors can share narrow trial progress while solo play remains complete.
- **Expanded data-driven Focus behavior palette** - new passive behavior types include `block_context_effect`, `navigation_hint`, `party_assist`, `use_item_window`, and `marked_target`.
- **Build setup metadata** - saved and imported Focus builds can carry advisory role, note, preferred party size, required Focus ids, and version context. This metadata never grants items or bypasses attunement rules.
- **Import validation and setup suggestions** - imported build codes are checked against the server Focus registry before saving; malformed names, slots, and metadata are rejected or downgraded to warnings.
- **Gameplay polish QA checklist** - added a manual QA checklist for combat feel, Pact loops, Confluence discovery, Resonant Surges, Circles, Updraft flight, onboarding, and journal clarity.

### Changed
- **Minecraft 1.21.11 gameplay parity** - this maintenance line now carries the same gameplay-polish systems as `latest` while preserving the Minecraft 1.21.11 Fabric target and dependency range.
- **Updraft release carried forward** - Attuned 1.5.5+mc1.21.11 includes the 1.5.1 Updraft Focus, smoother boost/brake controls, flight feedback, and PvP exhaustion safeguard.
- **Deep Lanterns documentation and journal pages** - the Attunement Journal now explains Circles, public attunement state, shared credit, party pings, and the Deep Lanterns faction.
- **Example datapack expanded** - the sample pack now covers more real authoring lanes with five example Foci and matching behavior files.
- **Gallery coverage refreshed** - Modrinth/CurseForge gallery sheets were updated so the current Focus roster and shipped item art remain visible.
- **Combat and support math centralized** - repeated damage/support logic now flows through shared helpers, including clearer direct-combat target checks and capped positive Luck stacking.
- **Reliquary/build workflows hardened** - preset save, import, apply, delete, metadata inference, and quick-apply paths now share stricter validation boundaries.

### Fixed
- Circle membership changes clean up contribution windows and sync updated snapshots after leaving, kicking, disbanding, or disconnecting.
- Friendly or invalid targets are filtered out of party-assist, hostile-only Focus, Pact, Apex, and contribution checks.
- Build-share imports reject malformed metadata, non-string slot ids, component-like names, and unknown required Focus ids before saving.
- Positive Luck modifiers are capped through shared logic to prevent runaway support stacking.
- Deep Lantern support effects use bounded cadences and same-dimension checks so navigation and rescue feedback cannot become global trackers.

### Internal
- **Why this patch is large** - the roughly 14k-line increase is the combined cost of Circle runtime, client HUD state, network payloads, party contribution rules, new Focus behavior schema, four complete Foci with item/model/texture/data definitions, expanded authoring docs, gallery updates, example datapack coverage, and a large regression-test net.
- **Regression coverage** - added tests for Circle policy, manager behavior, pings, snapshots, invite prompts, party HUD geometry, shared credit, party effects, action-bar routing, preset metadata/import validation, damage helpers, Luck stacking, Deep Lantern content, and block-context scans.
- **Repository validation coverage** - repository verification now checks more structure, source-marker hygiene, Focus definitions, behavior palettes, docs claims, gallery assets, release-facing feature counts, Python syntax, and transient-cache leaks.
- **CI gate expansion** - CI now installs Python tooling, runs repository checks, runs Python tests through unittest and pytest, builds/tests Gradle, verifies the release jar, and runs the Minecraft server smoke check.
- Refreshed dependency locking, Gradle verification metadata, version-profile docs, and release upload metadata for the Minecraft 1.21.11 maintenance line.

### Compatibility and migration notes
- Existing worlds do not need a data migration for Circles. Circle state is transient server runtime state.
- Solo play remains complete. Circle systems add coordination, public role hints, and eligible shared credit, but they do not replace solo Pact, Focus, or Confluence progression.
- Author datapacks using older Focus behavior palette entries continue to work; the new behavior types are additive.
- This maintenance release targets Minecraft 1.21.11. The `latest` release line targets newer Minecraft versions separately.
