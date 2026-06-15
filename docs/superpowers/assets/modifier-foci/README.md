# Modifier Focus Art

This folder keeps the generated source and verification material for the 16 new
pure-modifier Foci.

- `modifier-foci-source.png` is the image-generated 4x4 source sheet.
- `modifier-foci-preview.png` previews the first frame of each final item strip.
- `asset-verification.json` records the generated file dimensions and source
  mapping.

Regenerate the game-facing assets with:

```powershell
python tools\generate_modifier_focus_assets.py
```

The importer writes animated 64x512 item texture strips for:

- Tide: `tidewarden_focus`, `wellspring_focus`, `current_runner_focus`,
  `saltbrand_focus`, `ebbstride_focus`
- Verdant: `overgrowth_focus`, `deeproot_focus`, `briarcoat_focus`,
  `fernstride_focus`, `sapflow_focus`
- Forge: `cinderplate_focus`, `bellowsfury_focus`
- Fury: `bloodrush_focus`, `ravager_focus`
- Bastion: `granitehide_focus`, `hammerward_focus`

The importer crops the generated source sheet, removes the flat magenta
background, resizes each icon to the Minecraft item texture size, and assembles
the existing eight-frame focus animation format.
