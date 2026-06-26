# Loader Port Checklist: forge-1.20.6

Loader: `forge` (Forge)
Minecraft: `1.20.6`
Java: `21`
Status: `candidate`
Branch: `forge/1.20.6`
Artifact: Dedicated Forge branch build candidate; release needs hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ForgeGradle 7.0.29`
- `loader_dependency`: `Forge 50.2.8`
- `mod_file`: `src/main/resources/META-INF/mods.toml`
- `networking`: `Modern branch-local owner state payload`
- `state_storage`: `Forge branch-local state bridge`

## Required Verification

- [ ] `python tools/verify_repository.py`
- [ ] `.\gradlew.bat test --tests dev.attuned.client.ForgeStateSyncHudContractTest --no-daemon`
- [ ] `.\gradlew.bat build --no-daemon`
- [ ] `Forge dedicated server smoke`
- [ ] `Forge client combat HUD smoke`

## Notes

- 2026-06-25 patch invalidates AttunementReadout after owner state sync.
- Build passes after pinning ForgeGradle to 7.0.29.
- Release still needs hands-on combat HUD resonance fill verification.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Forge explicitly.
