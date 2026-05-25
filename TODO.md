# Attuned TODO

This list is for the next development pass. Keep the first pass focused on
player clarity and tuning before adding more systems.

## Recommended Next Pass

- [ ] Fix altar readability and add a capacity preview.
- [ ] Add dormant Focus reason tooltips.
- [ ] Add two to four vanilla-feeling utility Foci from the backlog below.
- [ ] Add a short "first builds" journal page after the new Foci are settled.

## High Priority

- [ ] Improve Attunement Altar text readability.
  - Current analysis: `AltarScreen` draws the title, inventory label, capacity
    readout, stance label, forecast line, and hint text with `LABEL_DARK`
    (`0xFF404040`) over `textures/gui/altar.png`, which is mostly dark
    violet/stone. The contrast is too low, especially for the title area and
    the status/hint lines.
  - Try a light label color for altar text, such as pale lavender/off-white,
    and keep the stance color signal in the gem/accent instead of relying on
    dark text.
  - Test with GUI scale 2 and 3, plus a small window, because pixel-font
    readability changes quickly with scale.
  - Check every dynamic string state: empty shard slot, ready to bind, capacity
    full, Discord stance, each committed affinity, Pact active, and Apex ready.
  - Verify the Bind button label remains readable in enabled, hovered, and
    disabled states.
  - Add or update a lightweight UI contract test if the fix introduces new
    texture dimensions, text color constants, or renderer wiring.

- [ ] Make dormant Focus reasons clearer in the UI.
  - Show whether a Focus is dormant because it is over capacity, a duplicate of
    a unique Focus, or below the active priority cutoff.
  - Surface the reason in tooltips and the Focus panel without adding tutorial
    paragraphs to the screen.

- [ ] Add a short "first builds" section to the Attunement Journal.
  - Give players a few recommended early setups, such as mobility, survival,
    and combat examples.
  - Keep the examples tied to actual Focus names and capacity costs so players
    can copy them directly.

- [ ] Add a compact altar preview for the next capacity increase.
  - Show current capacity, next capacity, cap, and shard count in one readable
    line or icon-backed row.
  - Example target state: `Capacity 8 -> 10 / 20` when a shard is inserted.
  - Avoid crowding the shard slot and Bind button.

## Balance and Config

- [ ] Split loot tuning beyond the single `focus_loot_chance` value.
  - Consider config keys for common, rich, and treasure structure multipliers.
  - Consider a toggle or multiplier for shard fragments separately from Foci.
  - Preserve the current defaults unless testing shows survival progression is
    too fast.

- [ ] Recheck Focus and shard progression in structure-heavy modpacks.
  - Default Focus chances are about 17.5% in common chests, 25% in rich chests,
    and 45% in treasure chests.
  - Default shard-fragment chances are double those Focus chances.
  - Test with Lootr and at least one structure-generation modpack profile.

- [ ] Audit Pacts, Apex, and Resonance onboarding.
  - Confirm players can tell what unlocked, what is dormant, and what action
    wakes the next layer.
  - Make sure the combat HUD explains state through icons/readouts, not hidden
    rules.

## Content Quality

- [ ] Review all Focus tooltips for consistent scan order.
  - Suggested order: name, affinity/faction, cost, unique marker, effect, lore.
  - Make sure neutral Foci and Unseen faction Foci read clearly.

- [ ] Add a small checklist for adding a new Focus.
  - Include item registration, Focus definition JSON, model, texture, language
    keys, optional behavior, and validation command.
  - Link it from `docs/README.md` or keep it near `docs/adding-a-focus.md`.

- [ ] Add targeted tests for content contracts.
  - Ensure every registered Focus has a matching Focus definition, item model,
    texture, language name, lore, and effect text.
  - Ensure every behavior id referenced by data is registered in code.

## Vanilla-Esque Expansion Backlog

- [ ] Keep new systems inside vanilla-style guardrails.
  - Prefer existing Minecraft actions: mining, sleeping, eating, blocking,
    sneaking, sprinting, bells, campfires, beds, compasses, and weather.
  - Avoid new ores, currencies, dimensions, or large crafting chains unless a
    later design pass proves they are necessary.
  - Keep each Focus narrow: one readable fantasy, one tradeoff, one obvious
    trigger.
  - Make additions useful but not mandatory for vanilla survival.
  - Let the altar stay a ritual/workbench/feedback object, not a quest hub.

- [ ] Prototype a Campfire or Hearth Focus.
  - Campfire version: while near a lit campfire, natural regeneration or food
    sustain is slightly better.
  - Hearth version: after sleeping, gain a short defensive or movement buff
    based on committed affinity.
  - Keep the effect gentle so it feels like survival flavor, not a required
    buff station.

- [ ] Prototype an Anchor or Ward Focus.
  - Anchor version: while sneaking, blocking, or using a shield, gain knockback
    resistance.
  - Ward version: after taking damage, briefly reduce the next hit only.
  - Best fit: Bastion, defensive, reactive, easy to read.

- [ ] Prototype a Lantern Focus.
  - In darkness, holding a torch or lantern briefly reveals nearby hostile mobs
    with a subtle Glowing effect or particles.
  - Keep range short and avoid wallhack-style behavior.
  - Best fit: neutral utility or Bastion support.

- [ ] Prototype a Rainstep Focus.
  - Move slightly faster in rain, shallow water, or waterlogged terrain.
  - Best fit: Zephyr or neutral mobility.
  - Keep it situational so it does not replace Swift Focus.

- [ ] Prototype a Cinder Focus.
  - Melee hits against burning enemies deal slightly more damage.
  - Best fit: Fury synergy with fire, lava, Flame, Fire Aspect, or Pyresworn.
  - Avoid adding another always-on raw damage Focus.

- [ ] Prototype a Forager Focus.
  - Leaves, grass, crops, or berry bushes have a tiny chance to provide extra
    seeds/food while the Focus is active.
  - Best fit: neutral exploration and early survival.
  - Keep output low to avoid replacing farms.

- [ ] Prototype a Tremor Focus.
  - Mining stone occasionally emits a nearby ore hint through sound or particles.
  - Do not draw outlines, reveal exact blocks, or behave like x-ray.
  - Best fit: neutral utility or Bastion earth flavor.

- [ ] Prototype a Waystone-style recovery Focus.
  - Compass behavior could point toward the last bed, last death location, or a
    recently bound altar.
  - Compare against Beacon Focus first so the two do not overlap too much.
  - Keep recovery useful without trivializing exploration risk.

- [ ] Add altar cosmetic ritual hooks.
  - Nearby candles, amethyst clusters, or committed affinity could alter
    particles and sound only.
  - Avoid tying power progression to decorative block placement.

- [ ] Add altar status feedback.
  - Empty-hand interaction or GUI text can summarize capacity, active Foci, and
    stance.
  - Keep it short enough for a chat line or compact panel row.

- [ ] Add advancement hooks for core milestones.
  - First Focus equipped.
  - First dormant Focus.
  - First Pact.
  - First Discord build.
  - First Apex state.

## Later Ideas

- [ ] Consider optional per-world or per-player discovery tracking.
  - This could support journal entries that unlock after finding a Focus, shard,
    Pact, or Apex state.
  - Keep it optional; do not block core mechanics behind discovery state.
