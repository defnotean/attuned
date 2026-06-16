# Attuned 1.5.2 - manual playtest checklist

Run on Minecraft 26.2 with `mod_version=1.5.2`. Check each box before publishing.

## Minecraft 26.2 Runtime

- [ ] Dedicated server starts cleanly with Fabric Loader 0.19.3 and Fabric API 0.152.1+26.2.
- [ ] Client dev runtime reaches the title screen without Attuned mixin, renderer, toast, or screen errors.
- [ ] Attuned HUD, Focus panel, Attunement Journal, Altar, and Reweaving Altar render without blank or broken textures.
- [ ] Tremor ore outlines render through the 26.2 submit-node path.

## Updraft Regression

- [ ] Equip Updraft Focus and a functional elytra, then start fall-flying.
- [ ] Hold jump and confirm the player boosts forward based on look direction without needing to look up or down.
- [ ] Hold sprint/control while fall-flying and confirm the player brakes hard.
- [ ] Confirm boost, brake, and exhaustion feedback particles/sounds are readable but not noisy.
- [ ] Keep PvP pressure going for more than five seconds and confirm Updraft falters with the exhaustion brake plus short Weakness and Slowness.

## Release Regression

- [ ] `python tools/verify_repository.py` passes.
- [ ] `.\gradlew.bat clean build --no-daemon` passes.
- [ ] `python tools/publish_curseforge.py --dry-run` shows `attuned-1.5.2.jar` and the 1.5.2 changelog section.
- [ ] `.\gradlew.bat modrinth --dry-run --no-daemon` passes.
