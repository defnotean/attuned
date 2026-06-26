# Loader Port Checklist: quilt-compat-1.21.11

Loader: `quilt-compat` (Quilt compatibility)
Minecraft: `1.21.11`
Java: `21`
Status: `blocked`
Branch: `port/quilt-compat-1.21.11`
Artifact: Existing 1.21.11 Fabric jar tested on Quilt Loader and QFAPI.

## Metadata

- `api_dependency`: `QFAPI for 1.21.11`
- `mod_file`: `src/main/resources/fabric.mod.json`
- `runtime_loader`: `quilt-loader`

## Required Verification

- [ ] `Confirm Quilt Loader and QFAPI versions exist for 1.21.11`
- [ ] `python tools/verify_repository.py`
- [ ] `Quilt dedicated server smoke`
- [ ] `Quilt client smoke`

## Notes

- Current audit found no matching 1.21.11 QFAPI line, so this cannot be claimed compatible yet.
- Use the matching Fabric maintenance branch as the artifact source.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt compatibility explicitly.
