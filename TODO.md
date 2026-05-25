# Attuned TODO

This list is for the next development pass. Keep the first pass focused on
player clarity and tuning before adding more systems.

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

## Later Ideas

- [ ] Consider optional per-world or per-player discovery tracking.
  - This could support journal entries that unlock after finding a Focus, shard,
    Pact, or Apex state.
  - Keep it optional; do not block core mechanics behind discovery state.

- [ ] Consider a compact altar preview for the next capacity increase.
  - Show current capacity, next capacity, cap, and shard count in one readable
    line or icon-backed row.
  - Avoid crowding the shard slot and Bind button.
