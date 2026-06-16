# Aspect Counter Replacement Focus Art

This folder keeps the generated source and verification material for the
selected Aspect Counter Focus replacements:

- `bramblegate_focus`
- `seedcall_focus`
- `riptide_heart_focus`
- `pearlguard_focus`
- `slagbrand_focus`

`aspect-counter-replacements-source.png` is the image-generated 5x1 source
sheet. `aspect-counter-replacements-preview.png` previews the first frame of
each final animated item texture. `asset-verification.json` records the source
mapping and generated dimensions.

Regenerate the game-facing assets with:

```powershell
python tools\generate_aspect_counter_replacement_assets.py
```

The importer crops the generated source sheet, removes the flat magenta
background, resizes each icon to the Minecraft Focus texture size, and assembles
the existing eight-frame Focus animation format.
