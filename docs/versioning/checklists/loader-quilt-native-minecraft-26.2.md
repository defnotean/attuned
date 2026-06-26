# Loader Port Checklist: quilt-native-26.2

Loader: `quilt` (Quilt)
Minecraft: `26.2`
Java: `25`
Status: `blocked`
Branch: `port/quilt-native-26x`
Artifact: Dedicated Quilt jar after quilt.mod.json and Quilt build are implemented.

## Metadata

- `api_dependency`: `QFAPI target version`
- `gradle_plugin`: `org.quiltmc.loom`
- `loader_dependency`: `quilt_loader target version`
- `mod_file`: `src/main/resources/quilt.mod.json`

## Required Verification

- [ ] `python tools/verify_repository.py`
- [ ] `Quilt build`
- [ ] `Quilt dedicated server smoke`
- [ ] `Quilt client smoke`

## Notes

- Native Quilt cannot reuse Fabric initializer classes directly; add Quilt initializer adapters before adding quilt.mod.json.
- Current audit found no matching QFAPI support for 26.x.
- Start only after compatibility smoke determines whether a native artifact is needed.
- Native Quilt metadata must not be committed to latest until a Quilt build task exists.

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names Quilt explicitly.
