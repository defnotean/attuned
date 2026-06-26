# Loader Port Checklist: forge-1.21.11

Loader: `forge` (Forge)
Minecraft: `1.21.11`
Java: `21`
Status: `candidate`
Branch: `forge/1.21.11`
Artifact: Dedicated Forge branch build candidate; release needs hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ForgeGradle`
- `loader_dependency`: `Forge version matching 1.21.11`
- `mod_file`: `src/main/resources/META-INF/mods.toml`
- `networking`: `Forge branch-local networking`
- `state_storage`: `Forge persistent attachment bridge`

## Required Verification

- [ ] `python tools/verify_repository.py`
- [ ] `.\gradlew.bat test --tests dev.attuned.attunement.ForgeAttachmentPersistenceContractTest --tests dev.attuned.client.ForgeOwnerStateSyncContractTest --tests dev.attuned.client.ForgeHudBridgeContractTest --no-daemon`
- [ ] `.\gradlew.bat build --no-daemon`
- [ ] `Forge dedicated server smoke`
- [ ] `Forge client smoke`
- [ ] `Hands-on resonance HUD smoke`

## Notes

- 2026-06-25 patch adds explicit owner-client AttunementStatePayload sync and HUD readout invalidation.
- 2026-06-25 persistence patch retains attachment codecs and reads/writes player persistent data for reconnect parity.
- Focused contracts and build pass.
- Release still needs hands-on resonance HUD fill verification.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Forge explicitly.
