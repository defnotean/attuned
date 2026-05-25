# Custom UI Art Pass Design

## Goal

Give Attuned's main player-facing surfaces a custom pixel-art identity while keeping them readable, vanilla-friendly, and modpack-safe.

## Visual Direction

The UI should feel like polished deepslate and amethyst: dark stone frames, bright mineral highlights, and small affinity-colored signals. It should still sit comfortably beside Minecraft's inventory/book UI, so edges stay pixel-sharp, text stays vanilla-font friendly, and the center of the playfield stays clear.

## Surfaces

- Attunement Altar: replace the flat solid-fill panel with a full custom GUI texture, including a ritual header, shard socket, inventory wells, and stance accent zones. Dynamic text, capacity fill, shard slot contents, and the Bind action remain code-driven.
- Focus panel: add a custom side-panel texture and matching active/dormant/priority accents. It must continue to attach beside the inventory and preserve the existing slot geometry.
- Combat HUD: keep the compact above-hotbar layout, but add custom backplates and clearer resonance/apex/target treatment so combat state reads faster.
- Journal: keep the item as a real written book, improve the item texture, and expand the guide copy into a better field guide.
- Tooltips: tighten information hierarchy without adding large overlays.

## Compatibility

The pass must not change save data, networking, gameplay balance, loot behavior, or slot positions. Lootr compatibility remains unchanged. New assets live under `assets/attuned/textures/gui/` or existing item texture paths and are referenced through normal resource identifiers.

## Testing

Add file-level tests that prove the expected custom UI assets exist, are valid PNG files, have the intended dimensions, and remain wired from the Java renderers. Existing unit tests and the full Gradle build must pass before release.
