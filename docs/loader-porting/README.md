# Loader Porting Notes

Attuned starts as a Fabric project. Multi-loader work follows this rule:

1. Keep the current Fabric build green.
2. Extract common code only when a second loader needs it.
3. Keep loader APIs inside adapter modules or clearly named adapter packages.
4. Publish one loader/version pair only after that pair has fresh server and
   client smoke evidence.

## Current Tracks

| Track | Status | First evidence required |
| --- | --- | --- |
| Fabric | Current artifact | Existing Gradle build, server smoke, and platform dry runs |
| Quilt compatibility | Blocked or planned by version | Quilt Loader + QFAPI dependency availability, server smoke, client smoke |
| Quilt native | 1.19.2 and 1.20.6 branch build/server-smoke candidates; newer targets blocked or planned by dependency availability | `quilt.mod.json`, Quilt initializer adapters, Quilt build, server smoke, client smoke, hands-on combat HUD smoke |
| NeoForge | 1.20.6, 1.21.1, 1.21.11, 26.1.2, and 26.2 branch build/server-smoke candidates; 1.20.1 blocked behind the legacy coordinate strategy; client smoke pending | `neoforge.mods.toml`, ModDevGradle build, server smoke, client smoke, hands-on combat HUD smoke |
| Forge | Candidate branch builds | `mods.toml`, ForgeGradle build, server smoke, client smoke, hands-on combat HUD smoke |

## Current Audit Snapshot

- Forge 1.18.2, 1.19.2, 1.19.4, 1.20.1, and 1.20.6 are branch build
  candidates after the 2026-06-25 resonance HUD fixes. They still need
  hands-on combat HUD smoke before release.
- Forge 1.21.1, 1.21.11, 26.1.2, and 26.2 now have explicit owner-client
  state sync, HUD readout invalidation, and codec-backed persistent attachment
  save/load. They still need hands-on combat HUD smoke before release.
- NeoForge 1.20.6, 1.21.1, 1.21.11, 26.1.2, and 26.2 now have metadata,
  `@Mod` entrypoint wiring, deferred item/block registration, NeoForge-backed
  Fabric-shaped shims for events/state/networking/client hooks, explicit
  owner-client state sync, HUD readout invalidation, scaffold pytest coverage,
  common/client compilation, full Gradle builds, and headless dedicated-server
  startups to `Done`. They are not release-ready until client smoke and
  hands-on resonance HUD fill verification pass.
- NeoForge 26.2 also updates the Tremor outline render shim to NeoForge
  `SubmitCustomGeometryEvent` and `SubmitNodeCollector`, matching the 26.2
  renderer API.
- NeoForge 1.20.1 remains blocked because the modern
  `net.neoforged:neoforge` artifact stream has no 1.20.1 coordinate; the
  available path is the legacy `net.neoforged:forge:1.20.1-47.1.106`
  coordinate and needs its own build strategy.
- `quilt/1.19.2` now has native Quilt metadata and entrypoint adapters:
  Quilt Loom, Quilt Loader 0.17.8, `quilt.mod.json`, an Attuned-owned access
  widener, Quilt Modrinth tags, and Fabric API compatibility supplied through
  `loader.addMods`. The branch passes scaffold pytest, dependency lock refresh,
  full Gradle build, and a headless dedicated server startup to `Done`. It is
  not release-ready until Quilt client smoke and hands-on resonance HUD fill
  verification pass.
- `quilt/1.20.6` now has native Quilt metadata, entrypoint adapters, QFAPI
  dependency wiring, owner-state HUD readout invalidation, scaffold pytest,
  common/client compilation, a full Gradle build, a headless server startup to
  `Done`, and a clean fatal-log scan after resource directory mirroring.
  It is not release-ready until Quilt client smoke and hands-on resonance HUD
  fill verification pass.
- Other Quilt compatibility/native targets remain blocked or planned by
  dependency availability and branch-local adapter work. The 1.19.2 native
  branch rejected QFAPI/QSL after aggregate and split-module runtime resolution
  failures in the audited loader combination.

## Module Intent

- `common`: loader-neutral gameplay, codecs, content data model, and shared tests.
- `fabric`: current Fabric adapter and artifact.
- `quilt`: optional Quilt-native adapter after Quilt compatibility smoke.
- `neoforge`: NeoForge adapter for modern Forge-family targets.
- `forge`: Forge adapter for older Forge-family targets.

## Compatibility First

Quilt starts as a compatibility validation track. A Quilt-native module is added
only after compatibility evidence shows it is needed or release platforms require
a dedicated Quilt file.

NeoForge remains the preferred modern Forge-family adapter target, but the
existing Forge branches are useful evidence for legacy APIs and HUD/state sync
risks. Do not publish any loader branch until its own release gates pass.
