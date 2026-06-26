# Loader Port Checklist: forge-1.19.4

Loader: `forge` (Forge)
Minecraft: `1.19.4`
Java: `17`
Status: `candidate`
Branch: `forge/1.19.4`
Artifact: Dedicated Forge branch build candidate; release needs hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ForgeGradle`
- `loader_dependency`: `Forge version matching 1.19.4`
- `mod_file`: `src/main/resources/META-INF/mods.toml`
- `networking`: `SimpleChannel`
- `state_storage`: `Forge branch-local state bridge`

## Required Verification

- [ ] `python tools/verify_repository.py`
- [ ] `.\gradlew.bat build --no-daemon`
- [ ] `Forge dedicated server smoke`
- [ ] `Forge client combat HUD smoke`

## Notes

- 2026-06-25 patch bridges HudRenderCallback through the 45.x RenderGuiOverlayEvent.Post API and invalidates AttunementReadout after owner state sync.
- Build passes; release still needs hands-on resonance HUD verification.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Forge explicitly.
