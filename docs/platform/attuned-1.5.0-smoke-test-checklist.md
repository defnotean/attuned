# Attuned 1.5.0 — manual playtest checklist

Run on a dedicated or local server with `mod_version=1.5.0`. Check each box before publishing.

## Discord build feel

- [ ] Equip a Discord build (mixed affinities, no single-affinity Pact) and fight vanilla mobs for ~2 minutes.
- [ ] Confirm damage feels roughly 1.20× baseline (not the old 1.33× spike) and is readable in combat.
- [ ] Toggle `discord_damage_multiplier` in config, reload, and confirm the change applies.

## Resonance → apex pulse → pact tactical (R)

- [ ] Enter a fight, deal damage, and watch resonance climb on the HUD.
- [ ] At ≥50% resonance, confirm the apex pulse / armed state appears.
- [ ] With an awake Pact and no competing Focus Ability, press **R** (Focus Ability) and confirm the **pact tactical** fires instead of a Focus ability.
- [ ] Reach apex resonance and confirm the tactical feels stronger and the HUD apex pulse is prominent.
- [ ] **Crouch** at ≥50% resonance and fire a pact tactical; confirm overcharge (stronger effect, −0.25 resonance, action-bar message).
- [ ] Repeat with resonance below 50% and confirm **R** does not fire the pact tactical.

## Ability Foci

- [ ] **Voidstep** (or another mobility ability Focus): equip, press **R**, confirm cooldown, particles, and movement.
- [ ] **Veil**: activate, confirm stealth/readout behavior and cooldown gate.
- [ ] **Mask**: activate, confirm disguise or mask effect and cooldown.
- [ ] **Pearlguard**, **Sparkweld**, **Oathguard**: each fires on **R**, respects cooldown, and matches tooltip/journal description.

## Affinity Loom (Altar of Reweaving)

- [ ] Place an Altar of Reweaving, open the menu, and confirm the **Affinity Loom** option is visible.
- [ ] Sacrifice one Focus plus shards; receive a **same-affinity** replacement (not a random cross-affinity roll).
- [ ] Loom again and confirm shard cost **escalates** on subsequent uses.
- [ ] Standard three-Focus reweaving still works as before.

## Build share / import

- [ ] Save a named build in a Focus Reliquary or Grand Focus Reliquary.
- [ ] Copy build to clipboard (`attuned:v1:` prefix).
- [ ] Paste into a second satchel on the same or another player; imported slots, names, and tempered flags match.
- [ ] Paste garbage or an old invalid code and confirm a clean error (no crash, no partial apply).

## Pact trial progress (journal)

- [ ] Awake a Pact and perform its trial action (e.g. Pyresworn ignite, Windrunner sprint near hostiles or at Apex).
- [ ] **Stoneheart**: confirm trial accrues only while **blocking** absorbed damage.
- [ ] **Forgebound**: confirm ignite trial accrues only with hostiles within ~16 blocks.
- [ ] **Wildroot**: Regen = full accrual; near hostiles without Regen = half; idle safe zone = no accrual.
- [ ] **Untethered**: confirm apex kills count only with nearby hostiles.
- [ ] Open Attunement Journal → **Pact Trials** page; counter advances and goal text matches in-game goals.
- [ ] Complete one trial: toast, advancement, Tier 4 bonus while that Pact is awake again, and onboarding hint if first completion.
- [ ] Re-awake the same Pact and confirm trial does not re-run (permanent unlock).

## Resonant surge (thunder)

- [ ] `/weather thunder` (or natural storm); wait for a surge broadcast.
- [ ] Stand in surge radius: resonance fills ~4× faster, spark column and ambient audio visible.
- [ ] On a Discord build, confirm surge fill is **half rate** vs a committed single-affinity build.
- [ ] Confirm only one surge is active at a time.

## Faction set bonus

- [ ] Equip **3+ active Unseen** Foci, sneak, and confirm **Speed** while sneaking.
- [ ] Drop below 3 active Unseen Foci; bonus stops.
- [ ] Spot-check one new 1.5 faction (**Tideborn**, **Forgebound**, **Wildroot**, or **Umbral**) at 3+ active Foci.

## HUD & journal polish

- [ ] Charged melee readiness dot appears when a charged hit is available.
- [ ] Tempered Focus shows tempered tick on HUD when equipped.
- [ ] Confluence pulse when a discovered Confluence is one Focus short.
- [ ] Journal **tempering** page explains tempered Foci and altar flow.

## Regression spot-checks

- [ ] Focus Reliquary save / apply / preview still works.
- [ ] Confluences still wake and show HUD pips.
- [ ] `/attuned validate` passes on the example datapack.
- [ ] Server starts clean (`tools/minecraft_runtime_smoke.py --accept-eula` or manual `runServer`).
