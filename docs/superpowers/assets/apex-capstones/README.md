# Apex Capstone Art

This folder keeps the source and verification material for the expanded Apex
capstone art pass.

- `apex-capstones-concept-source.png` is the image-generated concept sheet used
  as the visual source for the final pixel cleanup.
- `apex-capstone-assets-preview.png` previews the generated HUD sprites and the
  first frame of each new altar gem strip.
- `asset-verification.json` records the generated file dimensions.

Regenerate the game-facing assets with:

```powershell
python tools\generate_apex_capstone_assets.py
```

The script writes:

- HUD affinity sprites: `affinity_tide`, `affinity_forge`, `affinity_verdant`,
  `affinity_umbral`
- HUD capstone sprites: `riptide`, `crucible`, `bloomward`, `gloaming`
- Animated altar texture strips and block models for Tide, Forge, Verdant, and
  Umbral
