# Loader Port Checklist: quilt-compat-26.2

Loader: `quilt-compat` (Quilt compatibility)
Minecraft: `26.2`
Java: `25`
Status: `blocked`
Branch: `port/quilt-compat-26x`
Artifact: Existing Fabric jar tested on Quilt Loader and QFAPI when matching dependencies exist.

## Metadata

- `api_dependency`: `QFAPI for the target Minecraft version`
- `mod_file`: `src/main/resources/fabric.mod.json`
- `runtime_loader`: `quilt-loader`

## Required Verification

- [ ] `Confirm Quilt Loader and QFAPI versions exist for the Minecraft target before runtime smoke`
- [ ] `python tools/verify_repository.py`
- [ ] `Quilt dedicated server smoke`
- [ ] `Quilt client smoke`

## Notes

- Compatibility track only.
- Current audit found no matching QFAPI support for the 26.x target, so this cannot be claimed compatible yet.
- Do not relabel the Fabric jar as a Quilt-native artifact.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt compatibility explicitly.
