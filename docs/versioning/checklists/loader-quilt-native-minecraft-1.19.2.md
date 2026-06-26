# Loader Port Checklist: quilt-native-1.19.2

Loader: `quilt` (Quilt)
Minecraft: `1.19.2`
Java: `17`
Status: `candidate`
Branch: `quilt/1.19.2`
Artifact: Dedicated Quilt branch build/server-smoke candidate; release needs client runtime smoke and hands-on combat HUD smoke.

## Metadata

- `api_dependency`: `Fabric API 0.77.0+1.19.2 supplied to Quilt Loader through loader.addMods`
- `gradle_plugin`: `org.quiltmc.loom 1.15.1`
- `loader_dependency`: `Quilt Loader 0.17.8`
- `mod_file`: `src/main/resources/quilt.mod.json`
- `native_entrypoints`: `dev.attuned.quilt.AttunedQuilt and dev.attuned.quilt.AttunedQuiltClient`
- `rejected_qfapi`: `QFAPI/QSL 1.19.2 aggregate and split-module paths fail dev runtime resolution under the audited loader/metadata combination`

## Required Verification

- [x] `python -m pytest tests/test_quilt_scaffold_contract.py -q` passed, 4 tests.
- [x] `.\gradlew.bat dependencies --write-locks --no-daemon --stacktrace` passed.
- [x] `.\gradlew.bat build --no-daemon --stacktrace` passed.
- [x] `.\gradlew.bat runServer --no-daemon --stacktrace` reached `Attuned initializing` and server `Done (23.861s)` on 2026-06-25.
- [ ] `Quilt client smoke`
- [ ] `Hands-on combat HUD smoke`

## Notes

- quilt/1.19.2 now has Quilt Loom, quilt.mod.json, Quilt Loader dependency metadata, Quilt entrypoint adapters, an Attuned-owned access widener, Quilt Modrinth loader tags, and Fabric API compatibility supplied as the original aggregate jar through loader.addMods.
- The 2026-06-25 audit rejected QFAPI/QSL for this old branch because aggregate remapping drops nested module metadata, split QFAPI modules leave Fabric-id dependencies unsatisfied in this loader line, and newer Quilt Loader strict parsing rejects old QFAPI metadata.
- Server-side runtime proof passes: Quilt Loader loads Attuned plus Fabric API compatibility modules, logs Attuned initializing, and reaches server Done.
- Do not publish or claim public Quilt support until client smoke and hands-on resonance HUD fill verification pass.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt explicitly.
