# Expanded Apex Capstones Design

## Goal

Add Apex capstones for the two build identities that currently cannot reach Apex:

- **Discord Apex: Maelstrom** for all-three-affinity mixed builds.
- **Neutral Apex: Stillpoint** for all-neutral utility builds.

The existing Fury, Bastion, and Zephyr Apex capstones must keep their current rules and feel. The new capstones should read as first-class late-build rewards in chat, `/attuned status`, the Focus panel, the combat HUD, the journal, and generated HUD art.

## Current Shape

`Apex` currently resolves to `Optional<Affinity>`, so only Fury, Bastion, and Zephyr can be represented. That works for `Execute`, `Unyielding`, and `Untouchable`, but it leaves no clean way to express Discord or Neutral as Apex states. Discord is currently a glass-cannon stance, and Untethered is a Pact rather than an Apex. Neutral Foci deliberately do not create a committed affinity.

## Capstone Model

Introduce a capstone identity instead of representing Apex only as an affinity:

- `EXECUTE` for Fury.
- `UNYIELDING` for Bastion.
- `UNTOUCHABLE` for Zephyr.
- `MAELSTROM` for Discord.
- `STILLPOINT` for Neutral.

Each capstone exposes a display name, description, color, and optional affinity. Existing call sites that only need the old affinity behavior can use the optional affinity. UI and commands should use the capstone identity so Discord and Neutral show correctly.

## Qualification Rules

All Apex paths keep the same broad gate:

- At least 4 active Foci.
- Attunement capacity is nearly full, using the existing 1-point slack rule.
- The capstone only fires while Resonance is at least half full.

Single-affinity Apex remains unchanged:

- Fury Apex requires all active Foci to be Fury.
- Bastion Apex requires all active Foci to be Bastion.
- Zephyr Apex requires all active Foci to be Zephyr.
- Neutral active Foci still prevent these single-affinity Apexes.

Discord Apex qualifies as **Maelstrom**:

- Active Foci must include at least one Fury, one Bastion, and one Zephyr Focus.
- Neutral active Foci are allowed only if the all-three-affinity requirement is still met and the build is near full capacity.
- If Maelstrom qualifies, it wins over any ordinary Discord-only state.

Neutral Apex qualifies as **Stillpoint**:

- At least 4 active Foci.
- Every active Focus must be neutral, meaning it has no affinity.
- No Fury, Bastion, or Zephyr active Foci may be present.

## Capstone Effects

**Maelstrom** preserves Discord's risk and makes the mixed path explosive:

- Discord's existing glass-cannon multiplier remains: the player deals and takes 1.33x damage.
- While Maelstrom is active, direct player hits against affinity-bearing foes add a 10% damage kicker.
- The target is scrambled for 60 ticks after a hit. While scrambled, that target does not get affinity advantage damage against the Maelstrom player who scrambled it.

**Stillpoint** makes neutral utility builds defensive and unexploitable:

- While Stillpoint is active, affinity advantage damage against the player is suppressed.
- The player receives Absorption I for 60 ticks after recent combat, with a 160-tick per-player pulse cooldown.

## Resonance

The existing Resonance gauge still gates all Apex effects. Single-affinity resonance keeps its current favored-matchup gain/loss rules.

For Maelstrom and Stillpoint, add a generic combat resonance path:

- Maelstrom gains Resonance from damaging affinity-bearing enemies and killing hostile affinity-bearing enemies.
- Stillpoint gains Resonance from surviving hits from affinity-bearing attackers and from killing hostile enemies.
- Both still decay using the existing idle decay.

This keeps Apex active in real fights without forcing Discord or Neutral builds into a single rock-paper-scissors lane.

## Visual Assets

Create two new generated HUD sprites:

- `hud/maelstrom.png`: 64x64 Discord magenta sprite, fractured tri-affinity swirl, sharp and chaotic.
- `hud/stillpoint.png`: 64x64 pale neutral/white sprite, centered calm sigil, stable and minimal.

Use the `imagegen` workflow for source art, then normalize the outputs into Minecraft-ready PNG sprites. The final assets must live in the project under `src/main/resources/assets/attuned/textures/gui/sprites/hud/` and be referenced by the HUD renderer. Existing `execute`, `unyielding`, and `untouchable` sprite behavior stays unchanged.

The Focus panel and HUD render capstone sprites for all five Apexes. If generated art cannot be made readable at HUD size, use deterministic pixel-art cleanup while keeping the generated concept as the visual source.

## UI And Text

Update:

- Combat HUD gem routing for Maelstrom and Stillpoint.
- Focus panel readout so Apex can show all five capstones.
- `/attuned status` so Apex reports the new names and dormant/active state.
- Journal pages so Discord Apex and Neutral Apex have in-game explanations.
- Language strings for capstone names, descriptions, and dormant/active messages where needed.

## Testing

Add focused coverage for:

- Apex resolution chooses Execute, Unyielding, Untouchable, Maelstrom, or Stillpoint from representative active Focus layouts.
- Existing single-affinity Apex behavior remains unchanged.
- Maelstrom does not trigger from only two affinities.
- Stillpoint does not trigger if any affinity Focus is active.
- New HUD sprites exist at the expected dimensions and are referenced by the HUD code.
- Existing content consistency tests account for the new language and journal references.

Prefer a small pure resolver helper for capstone qualification so tests do not need a live Minecraft player instance.

## Non-Goals

- Do not add a fourth affinity to `Affinity`.
- Do not change the Fury > Bastion > Zephyr > Fury cycle.
- Do not remove Discord's glass-cannon downside.
- Do not rebalance existing Foci as part of this feature.
- Do not replace the existing single-affinity Apex sprites.
