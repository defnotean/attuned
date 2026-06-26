# Loader Port Checklist: neoforge-1.21.1

Loader: `neoforge` (NeoForge)
Minecraft: `1.21.1`
Java: `21`
Status: `candidate`
Branch: `neoforge/1.21.1`
Artifact: Dedicated NeoForge branch build/server-smoke candidate; release needs client runtime smoke and hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ModDevGradle 2.0.141`
- `loader_dependency`: `NeoForge 21.1.234`
- `mod_file`: `src/main/resources/META-INF/neoforge.mods.toml`
- `networking`: `RegisterPayloadHandlersEvent and PayloadRegistrar`
- `state_storage`: `NeoForge-backed Fabric compatibility shim with codec-backed persistent player data bridge and explicit owner-state payload sync`

## Required Verification

- [x] `python -m pytest tests/test_neoforge_scaffold_contract.py -q` passed, 6 tests.
- [x] `.\gradlew.bat compileJava --no-daemon --stacktrace` passed.
- [x] `.\gradlew.bat compileClientJava --no-daemon --stacktrace` passed.
- [x] `.\gradlew.bat build --no-daemon --stacktrace` passed.
- [x] `.\gradlew.bat runServer --no-daemon --stacktrace` reached `Attuned initializing` and server `Done (4.336s)` on 2026-06-25 after the explicit owner-state payload registration; Gradle was later stopped after startup because stdin was unavailable.
- [ ] `NeoForge client smoke`
- [ ] `Hands-on combat HUD smoke`

## Notes

- neoforge/1.21.1 has ModDevGradle metadata, neoforge.mods.toml, @Mod entrypoint wiring, Fabric-shaped NeoForge shims, persistent player state bridge, payload registration, client key/HUD/screen hooks, and optional Lootr metadata.
- The 2026-06-25 patch adds explicit owner-client `AttunementStatePayload` sync, registers the client receiver, and invalidates `AttunementReadout` after applying mirrored state; this is the resonance HUD fill path for the NeoForge client.
- compileJava, compileClientJava, pytest scaffold contracts, full Gradle build, and a headless dedicated server startup pass locally as of 2026-06-25.
- Do not publish or claim public NeoForge support until client smoke and hands-on resonance HUD fill verification pass.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names NeoForge explicitly.
