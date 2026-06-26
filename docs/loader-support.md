# Loader Support: Fabric, Quilt, NeoForge, and Forge

This guide documents how Attuned is organized for Fabric today and how a
Quilt, NeoForge, or Forge build should be added without blurring loader-specific
code into the gameplay/content layer.

Attuned also has an in-game **Forge** affinity. In this file, **Forge** means
the mod loader ecosystem unless the text explicitly says "Forge affinity".

## Current support status

| Loader track | Status | Artifact | Source of truth |
| --- | --- | --- | --- |
| Fabric | Shipping | `build/libs/attuned-<version>.jar` from the current Gradle build | `build.gradle`, `gradle.properties`, `src/main/resources/fabric.mod.json`, `config/minecraft-version-profiles.json` |
| Quilt compatibility | Blocked or planned by version | Existing Fabric jar tested on Quilt Loader + QFAPI where QFAPI exists, no relabeling | This guide, Quilt smoke checklist, QFAPI availability for the target Minecraft version |
| Quilt native | 1.19.2 and 1.20.6 branch build/server-smoke candidates; newer targets blocked or planned by dependency availability | Separate Quilt jar only after `quilt.mod.json`, Quilt initializer adapters, build, server smoke, client smoke, and hands-on HUD smoke pass | `quilt/*` worktrees and `src/main/resources/quilt.mod.json` |
| NeoForge | 1.20.6, 1.21.1, 1.21.11, 26.1.2, and 26.2 branch build/server-smoke candidates; 1.20.1 blocked behind the legacy NeoForge coordinate strategy; client smoke pending | Separate NeoForge jar required before publishing | `neoforge/*` worktrees and `src/main/resources/META-INF/neoforge.mods.toml` |
| Forge | Branch build candidates and blocked modern branches | Separate Forge jar required before publishing | `forge/*` worktrees, `docs/porting/forge-version-matrix.md`, and `src/main/resources/META-INF/mods.toml` |

Do not upload or label the Fabric jar as a Quilt, NeoForge, or Forge build. A
new loader release needs its own build target or compatibility evidence,
metadata, dependency declarations, smoke test, release notes, and platform
upload.

The detailed implementation roadmap lives in
`docs/superpowers/plans/2026-06-25-loader-port-roadmap.md`.

## Current worktree audit - 2026-06-26

The loader work is branch-scoped. The `latest` tree still builds the Fabric
artifact. Loader branch status is:

| Loader/version branch | Status | Required before release |
| --- | --- | --- |
| `forge/1.18.2`, `forge/1.19.2`, `forge/1.19.4`, `forge/1.20.1`, `forge/1.20.6` | Candidate branch builds. The 2026-06-25 pass fixed HUD render dispatch and same-tick resonance readout invalidation where needed, and each listed branch built successfully. | Hands-on combat HUD smoke: damage a valid target, confirm `/attuned status` resonance changes, and confirm the Combat/Foci resonance bar fills. |
| `forge/1.21.1`, `forge/1.21.11`, `forge/26.1.2`, `forge/26.2` | Candidate branch builds. The 2026-06-25 pass added explicit owner-client `AttunementStatePayload` sync, HUD readout invalidation, and codec-backed persistent attachment save/load for reconnect parity; each listed branch built successfully. | Run server/client and hands-on combat HUD smokes; do not infer resonance-bar behavior from build success alone. |
| `neoforge/1.21.1` | Candidate branch build/server smoke. The 2026-06-25 pass added ModDevGradle/NeoForge metadata, `@Mod` entrypoint wiring, deferred item/block registration, NeoForge-backed Fabric-shaped shims for events/state/networking/client hooks, codec-backed player persistent data, explicit owner-client `AttunementStatePayload` sync, client receiver registration, `AttunementReadout` invalidation after mirrored state applies, optional Lootr metadata, passing common/client compiles, a passing full Gradle build, and a headless dedicated server reaching `Done`. | Run client runtime smoke and hands-on combat HUD smoke: damage a valid target, confirm `/attuned status` resonance changes, and confirm the Combat/Foci resonance bar fills. |
| `neoforge/1.20.6` | Candidate branch build/server smoke. The 2026-06-25 pass backported the 1.21.1 adapter pattern to NeoForge `20.6.139`, refreshed dependency locks/verification metadata, added explicit owner-client `AttunementStatePayload` sync for the resonance HUD path, registered the client receiver, invalidated `AttunementReadout` after mirrored state applies, compiled common/client sources, passed the full Gradle build, and reached dedicated server `Done`. | Run client runtime smoke and hands-on combat HUD smoke; confirm loot additions append without replacing vanilla loot because this API line does not mutate existing archaeology pools in place. |
| `neoforge/1.21.11` | Candidate branch build/server smoke. The 2026-06-26 pass applied the verified ModDevGradle adapter pattern to NeoForge `21.11.42`, kept explicit owner-client `AttunementStatePayload` sync and `AttunementReadout` invalidation, passed scaffold pytest, compiled common/client sources, passed the full Gradle build, and reached dedicated server `Done`. | Run client runtime smoke and hands-on combat HUD smoke before release. |
| `neoforge/26.1.2` | Candidate branch build/server smoke. The 2026-06-26 pass applied the verified modern 26.x adapter pattern to NeoForge `26.1.2.76`, kept explicit owner-state sync and HUD cache invalidation, passed scaffold pytest, compiled common/client sources, passed the full Gradle build, and reached dedicated server `Done`. | Run client runtime smoke and hands-on combat HUD smoke before release. |
| `neoforge/26.2` | Candidate branch build/server smoke. The 2026-06-26 pass applied NeoForge `26.2.0.7-beta`, refreshed lock/verification metadata, updated Tremor outline rendering to NeoForge `SubmitCustomGeometryEvent`/`SubmitNodeCollector`, passed scaffold pytest, compiled common/client sources, passed the full Gradle build, and reached a clean second dedicated-server `Done` with no fatal log entries. | Run client runtime smoke and hands-on combat HUD smoke before release. |
| `neoforge/1.20.1` | Blocked. The official modern `net.neoforged:neoforge` artifact stream does not provide a 1.20.1 coordinate; the available 1.20.1 path is the legacy `net.neoforged:forge:1.20.1-47.1.106` coordinate, so it is not a direct copy of the verified ModDevGradle branches. | Choose and prove the legacy coordinate/build strategy before treating this as a candidate, then run scaffold contracts, common/client compile, build, server smoke, client smoke, and hands-on HUD smoke. |
| `quilt/1.19.2` | Native Quilt branch build/server-smoke candidate. The 2026-06-25 pass added Quilt Loom, Quilt Loader metadata, `quilt.mod.json`, Quilt common/client entrypoint adapters, an Attuned-owned access widener, Quilt Modrinth tags, and Fabric API compatibility supplied through `loader.addMods`. Scaffold pytest, dependency lock refresh, full Gradle build, and a headless dedicated server startup to `Done` pass. | Run Quilt client smoke and hands-on combat HUD smoke. Do not publish or claim public Quilt support until damage-driven resonance changes fill the Combat/Foci resonance bars on the client. |
| `quilt/1.20.6` | Native Quilt branch build/server-smoke candidate. The 2026-06-26 pass added the synced owner-state readout invalidation path, passed scaffold pytest, compiled common/client sources, passed the full Gradle build, reached server `Done`, and passed a follow-up fatal-log scan after resource directory mirroring removed dev resource-pack namespace warnings. | Run Quilt client smoke and hands-on combat HUD smoke before release. |
| Other `quilt/*` targets | Compatibility/native targets remain blocked or planned by dependency availability and branch-local adapter work. The 1.19.2 QFAPI path was audited and rejected for the native branch because aggregate remapping drops nested module metadata, split QFAPI modules leave Fabric-id dependencies unsatisfied, and newer Quilt Loader strict parsing rejects old QFAPI metadata. | Re-evaluate per Minecraft target; do not assume the 1.19.2 or 1.20.6 strategy is release-safe on newer targets without server and client smoke. |

## Shared Attuned layer

Keep the following content loader-neutral wherever possible:

- Focus definitions, behavior palettes, synergies, recipes, loot tables,
  advancements, worldgen, tags, language, textures, item models, and screen
  textures under `src/main/resources`.
- Pure gameplay resolution such as attunement budget math, active/dormant
  ordering, affinity matching, Discord/Apex/Pact resolution, build-share
  parsing, and data codecs.
- JSON schemas and datapack authoring rules documented in
  `docs/adding-a-focus.md`, `docs/authoring-foci.md`, and `docs/reference.md`.
- Test fixtures that assert content completeness, shipped behavior ids, and
  authoring contracts.

Loader adapters should own integration with the runtime:

- Mod metadata and dependency declarations.
- Entrypoints or main mod constructor wiring.
- Registries, creative tabs, menu types, commands, and datapack reload hooks.
- Player-persistent state storage and sync.
- Custom networking payload registration and send/receive plumbing.
- Client-only keybinds, HUD hooks, screens, tooltips, render hooks, and mixins.
- Config directory lookup and platform-specific optional dependency metadata.

The long-term shape should be either:

1. Keep the current single Fabric project and create a separate Forge-family
   branch that ports loader integration directly.
2. Split into `common`, `fabric`, `quilt`, `neoforge`, and `forge` modules once
   multiple loaders are actively maintained and the duplicated adapter code
   becomes costly.

Do not introduce a multi-loader abstraction until there is a verified second
loader build. For now, write the Forge-family port notes against real files and
real compatibility gaps.

## Fabric implementation

Fabric is the current working implementation.

### Build and metadata

- `build.gradle` applies `net.fabricmc.fabric-loom` through `loom_version`.
- `gradle.properties` carries the active Fabric tuple:
  `minecraft_version`, `loader_version`, `loom_version`,
  `fabric_api_version`, and `java_version`.
- `settings.gradle` includes the Fabric Maven repository.
- `src/main/resources/fabric.mod.json` declares mod id, entrypoints,
  dependencies, mixin configs, and optional/suggested integrations.
- `build.gradle` expands `fabric.mod.json` from Gradle properties and tags
  Modrinth uploads with `loaders = ["fabric"]`.
- CurseForge upload metadata currently marks the file as Minecraft/Fabric/Java
  and declares Fabric API as required.

### Entrypoints

The Fabric metadata points to:

- Common entrypoint: `dev.attuned.Attuned`, implementing `ModInitializer`.
- Client entrypoint: `dev.attuned.client.AttunedClient`, implementing
  `ClientModInitializer`.

Common initialization should register content, dynamic registries, commands,
server lifecycle hooks, networking payloads, persistent state attachments,
combat/event listeners, and data reload behavior. Client initialization should
stay client-only: keybinds, HUDs, screens, tooltip callbacks, client receivers,
render hooks, and client config.

### Fabric event map

Current Fabric integration relies on Fabric API events and callbacks such as:

- `CommandRegistrationCallback` for `/attuned`.
- `ServerTickEvents` for timed effects, onboarding, pacts, synergies, combat
  resonance, and network snapshot work.
- `ServerLifecycleEvents` and `ServerPlayConnectionEvents` for cleanup, reload,
  join, disconnect, and state reconciliation.
- `ServerLivingEntityEvents` and `ServerPlayerEvents` for combat, deaths,
  respawns, pact trials, Apex logic, and related progression.
- `PlayerBlockBreakEvents`, `UseBlockCallback`, and `UseItemCallback` for
  Focus behaviors that respond to world interaction.
- Client-side `ClientTickEvents`, `KeyMappingHelper`, `ItemTooltipCallback`,
  HUD/screen hooks, and client networking receivers.

Prefer Fabric events when one exists. Use mixins only for behavior that cannot
be reached through stable Fabric or vanilla hooks.

### Fabric state storage

`dev.attuned.attunement.AttunedAttachments` is the Fabric state boundary.
It uses Fabric's attachment API for:

- Capacity.
- Six Focus slots.
- Saved build presets.
- Milestones.
- Combat resonance.
- Onboarding flags.
- Pact trial progress.
- Discovered confluences.

Most player-owned data syncs target-only, so clients see their own attunement
state without exposing another player's private inventory or build metadata.
Public combat readouts use explicit server-mediated packets or action-bar text.

If code outside the state boundary needs attunement data, it should call the
Attuned helper methods rather than importing Fabric attachment APIs directly.
That keeps a future Forge-family port from needing to rewrite gameplay logic.

### Fabric networking

Fabric payloads are registered through `PayloadTypeRegistry` and sent/received
through `ServerPlayNetworking` and `ClientPlayNetworking`.

Rules for Fabric payloads:

- Register every clientbound and serverbound payload on the matching physical
  side before use.
- Keep payload records small and validate all client-originated data on the
  server.
- Reuse `StreamCodec` definitions where possible so payload structure stays
  testable and portable.
- Treat UI packets as requests, not authority. Saved builds, Focus movement,
  party/circle actions, and ability presses must still be checked server-side.
- Use targeted sends for private state and only broadcast public state that is
  already visible in gameplay.

Current networking surfaces include journal open/hints, Focus ability status,
reweaving, build presets, party/circle management, circle snapshots, pings, and
invite prompts.

### Fabric resources and client code

- Shared data and assets live in `src/main/resources`.
- Fabric common mixins live in `src/main/resources/attuned.mixins.json`.
- Fabric client mixins live in `src/client/resources/attuned.client.mixins.json`.
- Client Java lives in `src/client/java`.
- `fabric.mod.json` owns the entrypoint and mixin list; keep client-only mixins
  out of common runtime initialization.
- `AttunedConfig` and `AttunedClientConfig` currently use Fabric Loader config
  paths. A Forge-family build needs a separate config path adapter.

### Fabric local commands

Use these gates for the current Fabric artifact:

```bash
python tools/minecraft_version_profile.py validate
python tools/verify_repository.py
python -m unittest discover -s tests
python -m pytest tests/ -q
./gradlew test --no-daemon
./gradlew build --no-daemon
python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
```

Use `./gradlew runClient` when changing screens, HUDs, tooltips, keybinds,
client payload receivers, or render/mixin behavior.

## Quilt implementation

Quilt support has two tracks.

The first track is **Quilt compatibility**: test the existing Fabric artifact on
Quilt Loader with QFAPI for each target Minecraft version where QFAPI exists.
This remains useful for branches where a matching QFAPI line is healthy.

The second track is **Quilt native**: produce a dedicated Quilt jar with
`quilt.mod.json`, Quilt Loader dependency ranges, Quilt entrypoint adapters, and
separate platform upload tags. `quilt/1.19.2` is now the first native candidate.
It deliberately uses native Quilt metadata plus Fabric API compatibility at
runtime because the audited 1.19.2 QFAPI/QSL path is not reliable in this
dev/runtime setup.

Quilt work must not rename the Fabric jar as Quilt. A Quilt-compatible Fabric
file can be documented as compatible only after Quilt server and client smoke
pass. A Quilt-native file must have its own build output and metadata.

Current audit note: `quilt/1.19.2` and `quilt/1.20.6` have native metadata and
adapter layers in place. They replace `fabric.mod.json` with `quilt.mod.json`,
use Quilt Loom and Quilt Loader, delegate through
`dev.attuned.quilt.AttunedQuilt` and `dev.attuned.quilt.AttunedQuiltClient`,
and pass dedicated server startup. The 26.x and 1.21.11 lines remain blocked
until a matching API/runtime strategy exists.

### Quilt compatibility checklist

- Confirm Quilt Loader and QFAPI exist for the target Minecraft version.
- Install the current Fabric artifact into a Quilt Loader test instance with
  QFAPI instead of regular Fabric API.
- Run dedicated-server startup and fatal-log scanning.
- Run client startup through title screen and resource reload.
- Exercise Attuned screens, HUDs, keybinds, tooltips, custom payloads, and
  player-state persistence.
- Document the result as "Quilt-compatible Fabric file" only after that
  evidence exists.

### Quilt native checklist

- Add `src/main/resources/quilt.mod.json` only on the Quilt-native branch.
- Use Quilt Loader and a target-specific API strategy. Prefer QFAPI when it is
  available and runtime-proven for that Minecraft version; use Fabric API
  compatibility only when the branch docs record why QFAPI is not viable.
- Keep shared content under the common layer; keep Quilt-specific entrypoints,
  metadata, config, networking, and client setup in the Quilt adapter.
- Tag platform uploads as Quilt only for the dedicated Quilt jar.
- Keep Quilt release notes separate from Fabric release notes when loader
  requirements or known issues differ.

## Forge-family implementation

Forge-family support should be implemented as a separate loader target. For
modern versions, decide explicitly whether the target is Forge or NeoForge and
record that choice in the branch name, build file, release notes, and platform
metadata.

Forge-family status from the current worktree pass:

- Forge 1.18.2, 1.19.2, 1.19.4, 1.20.1, and 1.20.6 have candidate branch
  builds with the resonance HUD bridge/freshness fixes applied.
- Forge 1.21.1, 1.21.11, 26.1.2, and 26.2 now have explicit owner-client
  state sync, HUD readout invalidation, and codec-backed player persistent
  data save/load in the Forge attachment shim.
- NeoForge 1.20.6, 1.21.1, 1.21.11, 26.1.2, and 26.2 now have
  compiling/building branch adapters with NeoForge metadata, `@Mod` wiring,
  deferred item/block registration, event/state/networking/client shims, and
  codec-backed persistent player data. Their dedicated server smokes reach a
  running world. These branches have explicit owner-client
  `AttunementStatePayload` sync because the local attachment sync API is a
  compatibility shim, and they invalidate `AttunementReadout` after the
  mirrored state applies so HUD bars do not read a stale same-tick cache. They
  still need client smoke and hands-on combat HUD verification before release.

### Build and metadata

A Forge-family build should add, at minimum:

- A ForgeGradle or NeoGradle build path.
- A loader-specific source set or module so Fabric imports do not compile into
  the Forge-family artifact.
- `META-INF/mods.toml` for Forge, or `META-INF/neoforge.mods.toml` for
  NeoForge, with `attuned` id, version expansion, display metadata,
  Minecraft/loader dependency ranges, and optional integrations.
- Loader-specific mixin configuration or access transformer configuration.
- A jar classifier or output name that cannot be confused with the Fabric jar.
- Platform upload metadata that tags the file as Forge or NeoForge, not Fabric.

If the repository stays branch-per-loader, keep the Forge-family branch rebased
or merged from the matching Fabric content branch only after content tests are
green. If the repository becomes multi-module, isolate common code from both
loader APIs and make each adapter module depend on common.

### Forge-family entrypoint and lifecycle

Forge-family initialization should use the loader's mod class pattern:

- `@Mod(Attuned.MOD_ID)` for the main mod class.
- The mod event bus for registry and setup events.
- The main game event bus for gameplay events.
- A client-only setup path for screens, key mappings, HUD overlays, renderers,
  and client networking.

Do not run client code from the common/server initializer. Keep the same mental
split as the Fabric `ModInitializer` and `ClientModInitializer`, even though the
Forge-family lifecycle uses event buses instead of Fabric entrypoint names.

### Forge-family registry plan

Port registrations through the loader's registry system:

- Items, blocks, menu types, creative tabs, sounds, particles, and custom
  registries should use the loader's deferred/registry event pattern.
- Data-driven Attuned registries should keep the same resource ids and codecs
  so datapacks remain portable between loaders.
- Creative tab ordering must stay consistent with the Fabric build:
  Fury & Bastion, Zephyr & Holy, Tide & Forge affinity, Verdant & Umbral, and
  Utility & Tools.
- Menu opening and screen registration need a Forge-family adapter instead of
  relying on Fabric-widened constructors.

Keep loader imports out of content definition records and resolver classes. The
port should adapt registration plumbing, not redefine the Focus model.

### Forge-family state storage

Replace the Fabric attachment boundary with a Forge-family state adapter.

For Forge, this usually means capabilities attached to players and synced with
custom packets:

- Attach player capability/state during the appropriate entity attachment event.
- Persist capacity, Focus inventory, presets, milestones, resonance, onboarding,
  pact trials, and discovered confluences.
- Clone/copy state across respawn and dimension changes intentionally.
- Sync private state only to the owning player.
- Send public inspect/combat state through explicit public payloads or
  server-side action-bar text.

For NeoForge, evaluate the current data attachment/capability APIs for the
target version and document the exact choice in the port branch. The important
contract is the same: gameplay code reads and writes through Attuned state
helpers, while the loader adapter owns persistence and network sync.

### Forge-family event map

Port each Fabric callback to the nearest loader event for the target version.
Names vary across Forge/NeoForge releases, so keep a version-local mapping table
in the port branch. The required categories are:

| Attuned need | Fabric today | Forge-family target |
| --- | --- | --- |
| Common setup | `ModInitializer` | `@Mod` constructor plus mod lifecycle/setup event |
| Client setup | `ClientModInitializer` | client setup event or client-only subscriber |
| Commands | `CommandRegistrationCallback` | command registration event |
| Server ticks | `ServerTickEvents` | server tick event |
| Player join/leave | `ServerPlayConnectionEvents` | login/logout/player tracking events |
| Respawn/copy | `ServerPlayerEvents` | player clone/respawn events |
| Damage/death | `ServerLivingEntityEvents` | living hurt/damage/death events |
| Block breaking | `PlayerBlockBreakEvents` | block break event |
| Item/block use | `UseItemCallback`, `UseBlockCallback` | player interaction events |
| Datapack reload | Fabric lifecycle event | datapack/reload event |
| Tooltips | `ItemTooltipCallback` | item tooltip event |
| Keybinds | `KeyMappingHelper` | key mapping registration event |
| Client ticks | `ClientTickEvents` | client tick event |
| HUD/render | Fabric client render hooks/mixins | overlay/render events or targeted mixins |

When an event has different cancellation semantics from Fabric, write a small
adapter method and add a focused contract test around the gameplay result. Do
not assume a matching event name means matching timing.

### Forge-family networking

Forge and NeoForge use different networking APIs by version:

- Forge: a `SimpleChannel`-style channel is the expected path on many supported
  versions.
- NeoForge: current payload registration uses a payload registrar event and
  custom payload records.

For either target:

- Keep the existing payload ids where possible.
- Reuse vanilla/FriendlyByteBuf/StreamCodec-compatible encoding where possible.
- Define a protocol version and fail incompatible client/server combinations
  during login.
- Register both directions before gameplay.
- Server-validate all client-originated actions.
- Mirror Fabric's privacy rules for target-only attunement state.

Do not share networking classes directly if they import Fabric networking
types. Share the payload data shape and codec logic; keep send/receive handlers
in the loader adapter.

### Forge-family mixins and access

The Fabric build uses mixins for inventory UI, recipe book behavior, render
state, and invisibility presentation. A Forge-family port must review each one:

- Prefer loader events or extension points when they provide the same hook.
- Keep mixins only where no stable event exists.
- Use loader-supported mixin metadata and access transformer/access widener
  equivalents as required by the target.
- Re-test client startup, inventory screens, recipe book layout, thrown trident
  rendering, armor/hand/head invisibility, HUD overlays, and atlas/resource load.

### Forge-family config and integrations

- Replace Fabric Loader config path lookup with the Forge-family config path or
  config spec system.
- Keep the JSON config schema stable if possible so users can migrate between
  loaders.
- Declare Lootr as optional/suggested only when the target loader metadata can
  express that relationship.
- Do not add a hard Lootr API dependency unless Attuned adds custom loot
  containers or direct Lootr calls.

### Forge-family verification

A Forge-family branch is not publishable until it has its own evidence:

```bash
python tools/verify_repository.py
python -m unittest discover -s tests
python -m pytest tests/ -q
<forge-gradle-command> test
<forge-gradle-command> build
<forge-server-run-or-smoke-command>
<forge-client-run-command>
```

The exact Gradle task names belong in the Forge-family branch docs once the
build plugin is chosen. The verification report should include:

- Loader, Minecraft, Java, and mapping versions.
- Server startup with fatal log scanning.
- Client startup through title screen/resource reload.
- Attuned screens/HUD/tooltips visually checked.
- Focus inventory persistence across death, respawn, logout/login, and
  dimension change.
- Networking checks for ability presses, reweaving, presets, circle/party
  packets, journal opening, and private/public state boundaries.
- Loot injection and Lootr-compatible vanilla chest behavior.
- Platform dry-run metadata showing the correct loader tag.

## Version profile policy

`config/minecraft-version-profiles.json` currently describes the Fabric build
tuple. Keep using it for Fabric maintenance branches.

Forge-family support should not silently reuse those fields as if they were
complete. Add explicit Forge-family fields or a separate profile registry once
there is a real Forge/NeoForge build. Required fields should include:

- Minecraft version.
- Java target and build Java.
- Loader family: `quilt-compat`, `quilt`, `forge`, or `neoforge`.
- Quilt Loader/QFAPI version and version range when the loader family is Quilt.
- Forge/NeoForge version and version range.
- Gradle plugin/version.
- Mapping channel/version if it differs from Fabric.
- Optional dependency metadata for Lootr or other integrations.
- Status: current, candidate, maintenance, blocked, or dropped.
- Notes that name the source branch and verification evidence.

## Release policy

Every loader release needs separate metadata:

- Modrinth: set the file loader list to exactly the loader(s) in that jar.
- CurseForge: include the matching loader game version name and dependency list.
- README/platform descriptions: state the loader requirements for the file being
  published.
- Release notes: name Fabric, Quilt, Forge, or NeoForge explicitly when
  dependency or runtime work is loader-specific.
- Smoke checklist: include the exact loader, loader API, Minecraft, Java, and
  Gradle/plugin versions used for the file.

If Fabric, Quilt, NeoForge, and Forge builds are released for the same Attuned
version, keep their gameplay release notes aligned but keep loader requirements,
dependencies, known issues, and upload metadata separate.

## External loader docs

These official docs are the starting points for loader-specific port work:

- Fabric project structure: https://docs.fabricmc.net/develop/getting-started/project-structure
- Fabric events: https://docs.fabricmc.net/develop/events
- Fabric networking: https://docs.fabricmc.net/develop/networking
- Quilt setup and `quilt.mod.json`: https://wiki.quiltmc.org/introduction/setting-up
- Quilt QSL/QFAPI update note: https://quiltmc.org/en/blog/2024-07-03-qfapi-moving-forward/
- Forge events: https://docs.minecraftforge.net/en/latest/concepts/events/
- Forge capabilities: https://docs.minecraftforge.net/en/latest/datastorage/capabilities/
- Forge SimpleImpl networking: https://docs.minecraftforge.net/en/latest/networking/simpleimpl/
- NeoForge mod files: https://docs.neoforged.net/docs/gettingstarted/modfiles/
- NeoForge data attachments: https://docs.neoforged.net/docs/datastorage/attachments/
- NeoForge payload registration: https://docs.neoforged.net/docs/networking/payload/
