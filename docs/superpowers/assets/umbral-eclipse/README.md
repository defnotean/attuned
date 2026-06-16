# Umbral Eclipse Focus Art

This folder keeps the generated source and verification material for the
Umbral Eclipse Focus art pass.

- `umbral-eclipse-foci-source.png` is the image-generated 5x1 source sheet.
- `umbral-eclipse-foci-preview.png` previews the first frame of each final
  animated item texture.
- `umbral-eclipse-foci-report.json` records the generated file dimensions and
  source mapping.

Regenerate the game-facing assets with:

```powershell
python tools\generate_umbral_eclipse_focus_assets.py
```

The importer crops the generated source sheet, removes the flat magenta
background, resizes each icon to the Minecraft Focus texture size, and assembles
the existing eight-frame Focus animation format.
