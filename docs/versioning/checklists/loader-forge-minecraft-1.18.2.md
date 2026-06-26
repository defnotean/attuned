# Loader Port Checklist: forge-1.18.2

Loader: `forge` (Forge)
Minecraft: `1.18.2`
Java: `17`
Status: `candidate`
Branch: `forge/1.18.2`
Artifact: Dedicated Forge branch build candidate; release needs hands-on combat HUD smoke.

## Metadata

- `gradle_plugin`: `ForgeGradle`
- `loader_dependency`: `Forge version matching 1.18.2`
- `mod_file`: `src/main/resources/META-INF/mods.toml`
- `networking`: `SimpleChannel`
- `state_storage`: `Forge capabilities`

## Required Verification

- [ ] `python tools/verify_repository.py`
- [ ] `Forge build`
- [ ] `Forge dedicated server smoke`
- [ ] `Forge client smoke`

## Notes

- 2026-06-25 patch bridges HudRenderCallback through RenderGameOverlayEvent.Post and invalidates AttunementReadout after owner state sync.
- Build passes; release still needs hands-on resonance HUD verification.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Forge explicitly.
