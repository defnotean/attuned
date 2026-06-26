# Loader Port Checklist: neoforge-26.2

Loader: `neoforge` (NeoForge)
Minecraft: `26.2`
Java: `25`
Status: `candidate`
Branch: `neoforge/26.2`
Artifact: Dedicated NeoForge branch build/server-smoke candidate; release needs client runtime smoke and hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ModDevGradle 2.0.141`
- `loader_dependency`: `NeoForge 26.2.0.7-beta`
- `mod_file`: `src/main/resources/META-INF/neoforge.mods.toml`
- `networking`: `RegisterPayloadHandlersEvent and PayloadRegistrar`
- `state_storage`: `NeoForge-backed Fabric compatibility shim with codec-backed persistent player data bridge and explicit owner-state payload sync`
- `world_outline_rendering`: `SubmitCustomGeometryEvent and SubmitNodeCollector for the 26.2 renderer`

## Required Verification

- [ ] `python -m pytest tests/test_neoforge_scaffold_contract.py -q`
- [ ] `.\gradlew.bat compileJava compileClientJava --no-daemon --stacktrace`
- [ ] `.\gradlew.bat build --no-daemon --stacktrace`
- [ ] `.\gradlew.bat runServer --no-daemon --stacktrace (2026-06-26 clean second boot reached Attuned initializing and server Done; process tree stopped after startup because Gradle stdin was unavailable)`
- [ ] `NeoForge client smoke`
- [ ] `Hands-on combat HUD smoke`

## Notes

- neoforge/26.2 carries the verified modern 26.x ModDevGradle adapter pattern with the exact NeoForge 26.2.0.7-beta dependency tuple.
- The 2026-06-26 pass refreshed the NeoForge lock/verification metadata and updated the Fabric-shaped level-render shim to use NeoForge SubmitCustomGeometryEvent with event.getSubmitNodeCollector().
- The branch includes explicit owner-client AttunementStatePayload sync and invalidates AttunementReadout after applying mirrored state; this is the resonance HUD fill path.
- Scaffold pytest, common/client compilation, full Gradle build, and a clean second headless dedicated server startup to Done pass locally as of 2026-06-26.
- Do not publish or claim public NeoForge support until client smoke and hands-on resonance HUD fill verification pass.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names NeoForge explicitly.
