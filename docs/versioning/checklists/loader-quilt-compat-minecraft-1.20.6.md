# Loader Port Checklist: quilt-compat-1.20.6

Loader: `quilt-compat` (Quilt compatibility)
Minecraft: `1.20.6`
Java: `21`
Status: `planned`
Branch: `port/quilt-compat-1.20.6`
Artifact: Existing 1.20.6 Fabric jar tested on Quilt Loader and QFAPI.

## Metadata

- `api_dependency`: `QFAPI for 1.20.6`
- `mod_file`: `src/main/resources/fabric.mod.json`
- `runtime_loader`: `quilt-loader`

## Required Verification

- [ ] `Confirm Quilt Loader and QFAPI versions exist for 1.20.6`
- [ ] `python tools/verify_repository.py`
- [ ] `Quilt dedicated server smoke`
- [ ] `Quilt client smoke`

## Notes

- Newest plausible Quilt compatibility target found in the audit, but QFAPI embeds a slightly older Fabric API line than this branch.
- Use the matching Fabric maintenance branch as the artifact source only after dependency resolution is confirmed.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt compatibility explicitly.
