# Custom Focus Art

This folder keeps the generated source and verification material for the
resource-pack-skinnable `custom_focus_1` through `custom_focus_8` item pool.

- `custom-foci-source.png` is the image-generated 4x2 source sheet.
- `custom-foci-preview.png` previews the final static item textures.
- `asset-verification.json` records the generated file dimensions and source
  mapping.

Regenerate the game-facing assets with:

```powershell
python tools\generate_custom_focus_assets.py
```

The importer crops the generated source sheet, removes the flat magenta
background, and resizes each icon to a static 64x64 item texture. It does not
add FocusDefinition data or animation metadata; these items remain blank,
resource-pack-skinnable Focus slots for datapack authors.
