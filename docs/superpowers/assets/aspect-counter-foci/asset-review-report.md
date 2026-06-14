# Aspect Counter Foci — Asset Review Report

Date: 2026-06-13
Status: preview assets generated, not wired into gameplay yet

## Existing Focus asset setup audit

I reviewed the current Focus texture library before drawing the new Aspect Foci.

- Existing `*focus*.png` item textures found: **74**.
- Bespoke shipped Foci use the same setup: **64×512 animated PNG sheets**, eight **64×64** frames.
- Matching metadata convention: `.png.mcmeta` with:

```json
{
  "animation": {
    "frametime": 2,
    "interpolate": true
  }
}
```

- The only setup outliers are `custom_focus_1` through `custom_focus_8`. Those are intentional resource-pack-skinnable placeholder/default Foci and ship as static 16×16 art, unlike bespoke Foci.

Audit files:

- `tmp_focus_texture_sheets/all_existing_focus_assets_frame0_sheet.png`
- `tmp_focus_texture_sheets/all_existing_focus_assets_inventory16_sheet.png`
- `tmp_focus_texture_sheets/focus_asset_setup_audit.json`

## Established Attuned Focus aesthetic

The new icons should follow these rules:

1. Chunky centered medallion/talisman footprint.
2. Heavy dark/black outer outline.
3. Beveled rim with top-left highlight and bottom-right shadow.
4. Recessed colored field inside the rim.
5. One bold central motif per icon.
6. Aspect palettes are disciplined: 2–4 dominant colors, with glint/shadow only as accents.
7. Readability must be judged at Minecraft inventory scale, not just 64px source scale.
8. Avoid thin neon arcs, overly airy silhouettes, portrait-like detail, noisy rays, or generated-image gradients.

## New preview asset batch

Generated 12 planned Aspect Foci from the 8-Aspect counter-system spec:

### Tide

- `undertow_focus`
- `riptide_heart_focus`
- `pearlguard_focus`

### Forge

- `slagbrand_focus`
- `anvilheart_focus`
- `sparkweld_focus`

### Verdant

- `thornwake_focus`
- `seedcall_focus`
- `bramblegate_focus`

### Umbral

- `nullveil_focus`
- `cinderthief_focus`
- `snaremoon_focus`

The preview assets are stored under docs instead of `src/main/resources` because these Foci are not implemented as items/data yet:

- `docs/superpowers/assets/aspect-counter-foci/textures/item/`

They still use the real Focus texture setup: **64×512**, eight frames, `.png.mcmeta`, frametime 2, interpolation true.

## Preview sheets

- Existing references vs new icons: `docs/superpowers/assets/aspect-counter-foci/previews/new_aspect_foci_vs_existing_theme.png`
- New icons full-size: `docs/superpowers/assets/aspect-counter-foci/previews/new_aspect_foci_fullsize.png`
- New icons inventory-scale: `docs/superpowers/assets/aspect-counter-foci/previews/new_aspect_foci_inventory16.png`
- New icons all 8 animation frames: `docs/superpowers/assets/aspect-counter-foci/previews/new_aspect_foci_animation_frames.png`

## QA notes

- The new icons now share a consistent medallion/rim/bevel structure with the existing Focus family.
- Each Aspect has a recognizable palette: Tide cyan/teal, Forge orange/ember, Verdant green, Umbral violet/black.
- Each icon has one main motif and avoids the previous out-of-theme thin line-art problem.
- Inventory-scale preview remains readable overall.
- `anvilheart_focus` and `cinderthief_focus` were revised after the first preview pass because their initial motifs were weaker at 16px.

## Verification

Generated asset verification passed:

- Checked: **12** new preview Focus sheets.
- Size: all **64×512**.
- Frames: all **8** frames.
- Metadata: all have matching `.png.mcmeta` with `frametime: 2` and `interpolate: true`.
- Animation: all frames differ from frame 0.

Detailed verification JSON:

- `docs/superpowers/assets/aspect-counter-foci/asset-verification.json`

## Source generator

The deterministic generator lives in:

- `tools/generate_ui_art.py`

New function:

- `generate_aspect_focus_preview_textures()`

This keeps the preview sprites reproducible instead of being one-off raster edits.
