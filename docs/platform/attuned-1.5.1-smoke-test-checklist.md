# Attuned 1.5.1 - manual playtest checklist

Run on a dedicated or local server with `mod_version=1.5.1`. Check each box before publishing.

## Updraft Focus

- [ ] Equip Updraft Focus and a functional elytra, then start fall-flying.
- [ ] Hold jump and confirm the player boosts forward based on look direction without needing to look up or down.
- [ ] Release jump and confirm velocity settles smoothly instead of snapping.
- [ ] Hold sprint/control while fall-flying and confirm the player brakes hard.
- [ ] Hold jump and sprint/control together and confirm braking wins.
- [ ] Confirm boost, brake, and exhaustion feedback particles/sounds are readable but not noisy.

## PvP Exhaustion

- [ ] Damage or get damaged by another player while Updraft is active.
- [ ] Keep the PvP exchange going for more than five seconds and confirm Updraft falters.
- [ ] Confirm exhaustion applies a hard brake plus short Weakness and Slowness.
- [ ] Leave PvP pressure and confirm Updraft control recovers after the exhaustion window.

## Release Regression

- [ ] `python tools/verify_repository.py` passes.
- [ ] `.\gradlew.bat clean build --no-daemon` passes.
- [ ] `python tools/publish_curseforge.py --dry-run` shows `attuned-1.5.1.jar` and the 1.5.1 changelog section.
- [ ] Server starts clean (`tools/minecraft_runtime_smoke.py --accept-eula` or manual `runServer`).
