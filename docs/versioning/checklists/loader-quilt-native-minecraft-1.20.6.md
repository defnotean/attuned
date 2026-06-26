# Loader Port Checklist: quilt-native-1.20.6

Loader: `quilt` (Quilt)
Minecraft: `1.20.6`
Java: `21`
Status: `candidate`
Branch: `quilt/1.20.6`
Artifact: Dedicated Quilt branch build/server-smoke candidate; release needs client runtime smoke and hands-on combat HUD smoke.

## Metadata

- `api_dependency`: `QFAPI 10.0.0-alpha.3+0.100.4-1.20.6`
- `gradle_plugin`: `org.quiltmc.loom 1.15.1`
- `loader_dependency`: `Quilt Loader 0.30.0-beta.8`
- `mod_file`: `src/main/resources/quilt.mod.json`
- `native_entrypoints`: `dev.attuned.quilt.AttunedQuilt and dev.attuned.quilt.AttunedQuiltClient`
- `resonance_sync`: `client AttunementStateClient applies synced owner state and invalidates AttunementReadout`

## Required Verification

- [ ] `python -m pytest tests/test_quilt_scaffold_contract.py -q`
- [ ] `.\gradlew.bat compileJava compileClientJava --no-daemon --stacktrace`
- [ ] `.\gradlew.bat build --no-daemon --stacktrace`
- [ ] `.\gradlew.bat runServer --no-daemon --stacktrace (2026-06-26 reached Attuned initializing and server Done; stopped cleanly)`
- [ ] `Quilt client smoke`
- [ ] `Hands-on combat HUD smoke`

## Notes

- quilt/1.20.6 has Quilt Loom, quilt.mod.json, Quilt Loader dependency metadata, Quilt entrypoint adapters, QFAPI dependency wiring, Quilt Modrinth tags, and the owner-state HUD cache invalidation path.
- The 2026-06-26 pass added AttunementReadout invalidation after synced owner state applies so the resonance HUD does not read stale client cache data.
- Scaffold pytest, common/client compilation, full Gradle build, and a headless dedicated server startup to Done pass locally as of 2026-06-26.
- The second server-log audit found no ModNioResourcePack, NoSuchFileException, ERROR, or Exception entries after resource directory mirroring was added.
- Do not publish or claim public Quilt support until client smoke and hands-on resonance HUD fill verification pass.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt explicitly.
