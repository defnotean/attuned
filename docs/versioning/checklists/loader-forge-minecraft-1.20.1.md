# Loader Port Checklist: forge-1.20.1

Loader: `forge` (Forge)
Minecraft: `1.20.1`
Java: `17`
Status: `candidate`
Branch: `forge/1.20.1`
Artifact: Dedicated Forge branch build candidate; release needs hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ForgeGradle`
- `loader_dependency`: `Forge version matching 1.20.1`
- `mod_file`: `src/main/resources/META-INF/mods.toml`
- `networking`: `SimpleChannel`
- `state_storage`: `Forge capabilities`

## Required Verification

- [ ] `python tools/verify_repository.py`
- [ ] `.\gradlew.bat test --tests dev.attuned.client.ForgeHudBridgeContractTest --tests dev.attuned.client.ForgeResonancePipelineContractTest --no-daemon`
- [ ] `.\gradlew.bat build --no-daemon`
- [ ] `Forge dedicated server smoke`
- [ ] `Forge client combat HUD smoke`

## Notes

- 2026-06-25 patch bridges HudRenderCallback through RenderGuiOverlayEvent.Post and invalidates AttunementReadout after owner state sync.
- Focused contracts and build pass.
- Chosen because 1.20.1 is a high-value modpack target.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Forge explicitly.
