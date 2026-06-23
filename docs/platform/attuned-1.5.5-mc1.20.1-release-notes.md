## Attuned 1.5.5+mc1.20.1 - Gameplay Polish Backport

Attuned 1.5.5+mc1.20.1 brings the gameplay-polish release to the Minecraft 1.20.1 maintenance line. This is a large release because it is carrying over whole systems, not only a few item definitions: server-authoritative Circles, party-aware support, safer shared contribution windows, richer build import metadata, Deep Lantern exploration Foci, expanded datapack behavior palettes, branch-specific compatibility shims, updated galleries, and the tests that keep those pieces stable.

### Why this patch is large
- The source diff grows by roughly 14k lines because the patch adds complete gameplay systems with client, server, networking, data, asset, documentation, and test coverage.
- Circles alone require runtime state, invite and leave flows, membership cleanup, snapshot synchronization, ping routing, contribution tracking, config knobs, HUD rendering, and validation tests.
- The new Deep Lantern Foci ship as complete content: registered items, models, textures, language entries, Focus JSON, behavior JSON, journal/reference docs, gallery coverage, and release notes.
- Build sharing is no longer just a string import path. Presets now support sanitized role, note, party-size, warning, requirement, and version-context metadata, with server-side validation before anything is saved or applied.
- The behavior palette grew because the new Foci and authoring hooks need reusable data-driven behavior types instead of one-off Java-only logic.
- The regression test suite expanded so release checks cover party behavior, hostile/friendly filtering, preset validation, action-bar priority, Deep Lantern data, Luck stacking, damage math, payload contracts, and branch-specific API differences.
- Minecraft 1.20.1 needs compatibility code that newer branches do not need, including Java 17-safe source shape, legacy Fabric packet callbacks, `FriendlyByteBuf` payload serialization, old item NBT storage, old attribute modifier operations, older HUD mixins, and client rendering paths.

### New gameplay
- Added Circles, temporary server-authoritative expedition parties with create, invite, accept, leave, kick, disband, expiry, capacity, cooldown, disconnect cleanup, and snapshot sync behavior.
- Added party HUD state, invite prompts, and ping notices through dedicated networking payloads.
- Added server-accepted Circle pings. Pings are same-dimension, membership-checked, rate-limited navigation targets rather than live player tracking.
- Added shared contribution windows for eligible nearby Circle members. Combat, blocking, reveal, and support actions can share narrow progress when a member actually contributed; passive AFK proximity does not farm progression.
- Added party-aware Pact Trial support so coordinated groups can share eligible trial progress while solo play remains fully complete.
- Added action-bar message priority routing so high-value feedback, cooldown messages, party notices, and combat state are not casually overwritten by low-priority spam.

### New Foci
- Cavewick Focus rewards player-built cave routes by refreshing gentle Night Vision near lantern and soul-lantern routes.
- Glowline Focus points toward recent accepted Circle pings in the same dimension, giving groups a route aid without exposing live player positions.
- Rescueflame Focus periodically helps a nearby drowning Circle member with Water Breathing. It deliberately excludes the wearer and uses cooldowns so it is rescue support, not a personal underwater buff.
- Depthglass Focus reads a held lodestone compass target and gives restrained same-dimension navigation hints.
- The shipped roster is now 99 Foci across mobility, defense, combat, stealth, holy, seafaring, shadow, rescue, utility, and the eightfold affinity wheel.

### Build and preset polish
- Shared build codes can carry role, notes, preferred party size, warnings, required Focus ids, and version context as advice.
- Imported builds are checked against the server Focus registry before saving.
- Malformed names, invalid slot data, component-like entries, unknown requirements, and unsafe metadata are rejected or downgraded to warnings instead of silently mutating a saved build.
- Quick-apply, preview, save, delete, and import flows now share stricter validation paths.

### Datapack and authoring support
- Added `attuned:block_context_effect` for local block-tag context effects, used by Cavewick.
- Added `attuned:navigation_hint` for restrained hints toward accepted targets, used by Glowline and Depthglass.
- Added `attuned:party_assist` for cooldown-limited support effects on eligible Circle members, used by Rescueflame.
- Added `attuned:use_item_window` for short effects or modifiers after using matching items or tags.
- Added `attuned:marked_target` for charged-hit mark and consume patterns.
- Expanded the example datapack with working build-mark, canopy-step, rescue-assist, route-window, and navigation-hint examples.
- Updated authoring docs and reference docs for the larger palette and validation behavior.

### Combat and support polish
- Hostile-only checks are stricter across party assist, direct-combat Foci, Pacts, Apex hooks, and shared contribution rules.
- Friendly or invalid targets are filtered before support/combat code decides whether to fire.
- Pact death messages, tactical feedback, combat polish hooks, and resonance/surge messages now share clearer validation and routing.
- Positive Luck support effects are capped through shared logic so stacked support cannot become unbounded.
- Updraft flight polish from 1.5.1 remains included: hold jump to boost forward during elytra flight, hold sprint/control to brake, and sustained PvP pressure can trigger exhaustion after five seconds.

### Minecraft 1.20.1 compatibility notes
- This release targets Minecraft 1.20.1, Java 17, Fabric Loader 0.18.4+, and Fabric API 0.92.8+1.20.1.
- The branch keeps 1.20.1-compatible APIs for HUD rendering, creative/survival inventory mixins, registry access, item NBT, packet callbacks, payload serialization, attribute modifiers, and item rendering.
- Networking uses Fabric's legacy `FabricPacket`, `PacketType`, and `FriendlyByteBuf` flow on this line instead of newer `CustomPacketPayload` and stream-codec registration.
- Wellspring and Current Runner use underwater Dolphin's Grace behavior on this branch because Minecraft 1.20.1 lacks the newer `minecraft:water_movement_efficiency` attribute used by later lines.
- The gameplay intent is the same as newer branches, but some implementation details are intentionally branch-local so the 1.20.1 artifact builds and runs on the correct loader/runtime.

### Fixes
- Circle membership changes now clear contribution windows and sync snapshots so clients do not keep stale party rows after leave, kick, disband, or disconnect events.
- Build-share imports reject malformed metadata and unknown required Foci before saving.
- Deep Lantern effects use same-dimension checks, bounded cadences, and explicit targets so navigation and rescue features do not become global tracking tools.
- Party assist and hostile-only Focus logic no longer treats nearby Circle members as enemies.
- Release-facing docs and gallery sheets now cover the current Focus roster and the new gameplay surface.

### Validation coverage
- Added and updated contract tests for Circle policy, Circle manager behavior, pings, snapshots, invite prompts, party HUD geometry, shared contribution credit, party effects, action-bar routing, preset metadata/import validation, payload sanitization, damage formulas, Luck stacking, Deep Lantern content, block-context scans, and version-specific compatibility.
- Repository verification now checks generated/public assets, Focus definitions, behavior palettes, docs claims, gallery assets, release-facing feature counts, Python syntax, and transient-cache hygiene.
