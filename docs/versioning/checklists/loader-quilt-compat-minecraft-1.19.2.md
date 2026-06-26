# Loader Port Checklist: quilt-compat-1.19.2

Loader: `quilt-compat` (Quilt compatibility)
Minecraft: `1.19.2`
Java: `17`
Status: `planned`
Branch: `port/quilt-compat-1.19.2`
Artifact: Existing 1.19.2 Fabric jar tested on Quilt Loader and QFAPI.

## Metadata

- `api_dependency`: `QFAPI 4.0.0-beta.30+0.77.0-1.19.2`
- `fabric_api_source`: `fabric-api 0.77.0+1.19.2`
- `mod_file`: `src/main/resources/fabric.mod.json`
- `runtime_loader`: `quilt-loader`

## Required Verification

- [ ] `Confirm Quilt Loader and QFAPI resolve for 1.19.2`
- [ ] `python tools/verify_repository.py`
- [ ] `Quilt dedicated server smoke`
- [ ] `Quilt client smoke`
- [ ] `Hands-on state, HUD, keybind, screen, and networking smoke`

## Notes

- Recommended first Quilt compatibility proof target from the 2026-06-25 audit because Fabric API 0.77.0+1.19.2 matches the available QFAPI line exactly.
- This remains a compatibility test of the Fabric artifact, not a native Quilt jar.
- Do not document Quilt compatibility until the server and client smokes pass.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt compatibility explicitly.
