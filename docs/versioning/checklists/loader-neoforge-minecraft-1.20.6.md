# Loader Port Checklist: neoforge-1.20.6

Loader: `neoforge` (NeoForge)
Minecraft: `1.20.6`
Java: `21`
Status: `candidate`
Branch: `neoforge/1.20.6`
Artifact: Dedicated NeoForge branch build/server-smoke candidate; release needs client runtime smoke and hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ModDevGradle 2.0.141`
- `loader_dependency`: `NeoForge 20.6.139`
- `mod_file`: `src/main/resources/META-INF/neoforge.mods.toml`
- `networking`: `RegisterPayloadHandlersEvent and PayloadRegistrar`
- `state_storage`: `NeoForge-backed Fabric compatibility shim with codec-backed persistent player data bridge and explicit owner-state payload sync`

## Required Verification

- [x] `python -m pytest tests/test_neoforge_scaffold_contract.py -q`
- [x] `.\gradlew.bat compileJava --no-daemon --stacktrace`
- [x] `.\gradlew.bat compileClientJava --no-daemon --stacktrace` passed after wiring `AttunementStateClient.init()` and `AttunementReadout.invalidate(...)`.
- [x] `.\gradlew.bat build --no-daemon --stacktrace`
- [x] `.\gradlew.bat runServer --no-daemon --stacktrace` reached `Attuned initializing` and server `Done (5.068s)` on 2026-06-25; Gradle was later stopped after startup because stdin was unavailable.
- [ ] `NeoForge client smoke`
- [ ] `Hands-on combat HUD smoke`

## Notes

- The branch backports the 1.21.1 NeoForge adapter shape with ModDevGradle metadata, `neoforge.mods.toml`, `@Mod` entrypoint wiring, Fabric-shaped NeoForge shims, payload registration, client key/HUD/screen hooks, and optional Lootr metadata.
- The 2026-06-25 pass adds explicit owner-client `AttunementStatePayload` sync because attachment sync is a local compatibility shim, registers the client receiver from `AttunedClient`, and invalidates `AttunementReadout` after applying mirrored state; this is the resonance HUD fill path for the NeoForge client.
- On NeoForge 20.6, Attuned appends new loot pools through `LootTable.addPool(...)` instead of mutating existing archaeology pools in place.
- Do not publish or claim public NeoForge support until client smoke and hands-on resonance HUD fill verification pass.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names NeoForge explicitly.
