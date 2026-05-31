# Reweaving, Seafarers, HUD, And Journal Lore Design

## Summary

Add a dedicated **Altar of Reweaving** for Focus rerolling, a peaceful **Seafarers** faction centered on fishing and water utility, split client-side HUD toggles for own and enemy affinity displays, and a lore pass that ties Reweaving, Seafarers, Radiant, and Unseen into the Attuned journal.

This spec builds on:

- `2026-05-31-expanded-apex-capstones-design.md`
- `2026-05-31-radiant-holy-affinity-expansion-design.md`

The design stays vanilla-friendly. It should not add raw PvP power, bypass capacity limits, or turn Focus rerolling into cheap lootbox spam.

## Generated Source Art

Source concepts have been generated and copied into the repo for future asset work:

- `docs/superpowers/assets/reweaving-seafarers/altar-of-reweaving-concept.png`
- `docs/superpowers/assets/reweaving-seafarers/seafarers-focus-concepts.png`
- `docs/superpowers/assets/reweaving-seafarers/radiant-unseen-reweaving-journal-concept.png`

These are reference images, not final Minecraft textures. Implementation should redraw, crop, downscale, and pixel-clean final assets into the target resource paths rather than directly shrinking the generated concepts.

## Lore Pillars

**Attunement is memory made wearable.** Foci do not grant random magic. They wake old patterns in the player, and shards make more room for those patterns.

**Altars bind; Reweaving alters.** The Attunement Altar grows capacity. The Altar of Reweaving changes how existing Focus patterns are remembered.

**Radiant and Unseen are rival ethics, not good and evil.** The Radiant keep names, vows, public witness, holy light, and judgment. The Unseen keep masks, hidden routes, erased histories, and survival outside public judgment.

**Seafarers are peaceful carriers of memory.** They recover fragments, journals, and half-remembered routes from shores, wrecks, rivers, and fishing lines. They trade in guidance, not conquest.

**The HUD is a reading, not a machine.** Affinity marks, resonance, and target readings are the visible pressure of the player's active pattern. Client HUD settings control whether a player wants to see those readings.

## Altar Of Reweaving

Add a new block named **Altar of Reweaving**.

Suggested ids:

- Block: `attuned:altar_of_reweaving`
- Block item: `attuned:altar_of_reweaving`
- Screen title: `screen.attuned.reweaving_altar`

This is a separate block, not a new mode on the Attunement Altar. The normal Attunement Altar remains focused on shard binding and capacity growth.

### Recipe

Use vanilla materials plus Attuned progression materials:

- Center: `minecraft:loom`
- North/South/East/West: `minecraft:polished_deepslate`
- Corners: `minecraft:string`
- Top or bottom accent: `attuned:attunement_shard_fragment`

Exact shaped layout can be tuned during implementation, but the recipe should communicate loom, stone, thread, and Attuned memory without requiring rare combat drops.

### Reweaving Economy

The altar consumes:

- 3 Focus items
- 1 Attunement Shard Fragment

It produces:

- 1 new Focus item

Rules:

- Inputs are consumed only when the server can place the result into the output slot or player inventory.
- The output should avoid matching any sacrificed Focus item id when enough alternatives exist.
- The output never upgrades capacity, never guarantees a specific Focus, and never increases a Focus item's stats.
- Reweaving is a conversion and sink, not an enhancement system.
- Reweaving does not consume XP.
- A short server-side click cooldown prevents packet spam.

### Output Weighting

Use the loaded Focus registry as the output pool.

Default weights:

- Neutral Foci: normal weight.
- Player's committed affinity, if any: modest bonus weight.
- Discord or no committed affinity: no affinity bias.
- Seafarers Foci: positive fishing-table bias in loot, but no special reroll privilege.

When Holy ships, Holy participates like the other affinities. Radiant lore can be represented through Holy Focus weights without making Reweaving a Holy-only rite.

### UI And Menu

The Altar of Reweaving gets its own menu and screen:

- 3 Focus input slots.
- 1 catalyst slot that accepts only Attunement Shard Fragments.
- 1 output slot.
- 1 `Reweave` button.

The screen should show the current requirement state:

- missing Focus inputs
- missing catalyst
- result blocked because output cannot fit
- ready to reweave

All slot validation and result creation must be server-authoritative. Closing the screen returns unused inputs like the existing Attunement Altar menu.

## Seafarers Faction

Add a peaceful utility faction named **Seafarers**.

Suggested id:

- `attuned:seafarers`

They are mostly there for fishing, water travel, and quiet world flavor. They should not create a PvP lane.

### Mechanical Boundaries

All first-wave Seafarers Foci are neutral.

Seafarers Foci must not:

- increase player damage
- reduce incoming damage
- reveal enemy players
- apply combat debuffs
- increase combat movement in a way that becomes a PvP chase tool
- interact with Apex or Pact math except as neutral Foci

They may:

- improve fishing comfort
- add small fishing rewards that do not duplicate treasure
- preserve or mend fishing rods in small amounts
- help navigation around boats, shores, beds, or recent fishing locations
- add gentle water-adjacent quality of life

### First-Wave Foci

**Linecast Focus**

- Cost: 2
- Affinity: none
- Faction: `attuned:seafarers`
- Effect: successful fish catches have a small chance to grant one extra ordinary fish. Treasure is never duplicated.

**Netmender Focus**

- Cost: 2
- Affinity: none
- Faction: `attuned:seafarers`
- Effect: catching a fish can restore 1 durability to the fishing rod used, with a short cooldown.

**Harborlight Focus**

- Cost: 2
- Affinity: none
- Faction: `attuned:seafarers`
- Effect: near water at night, holding or standing near a lantern gives a brief navigation-friendly glow or night vision effect. It should not reveal players.

**Driftglass Focus**

- Cost: 3
- Affinity: none
- Faction: `attuned:seafarers`
- Effect: while boating or fishing, records a temporary safe return point. A held compass can point back to that point while the Focus is active.

Final numbers should be conservative. The point is to make fishing feel cared for, not to make Seafarers mandatory.

## HUD Settings

Add client-side settings for the combat HUD:

- `show_own_affinity_hud`, default `true`
- `show_enemy_affinity_hud`, default `true`

Use a client config file:

- `config/attuned-client.json`

Do not add a hard Mod Menu, Cloth Config, or YACL dependency for this pass. The settings should be accessible through Minecraft's Controls/settings by adding keybind rows:

- `key.attuned.toggle_own_affinity_hud`
- `key.attuned.toggle_enemy_affinity_hud`

Default keybinds should be unbound so the mod does not steal more keys.

### HUD Behavior

Both toggles are purely client-side visual preferences.

If own HUD is off:

- Hide the player affinity gem.
- Hide the resonance bar and own Apex/stance presentation.
- Do not disable abilities, Foci, Pacts, Apex, resonance, or attunement.

If enemy HUD is off:

- Hide the target/enemy affinity gem.
- Hide target matchup hints and target-derived halos/tints.
- Do not change combat math.

If own HUD is off but enemy HUD is on:

- Draw a compact enemy-only target readout.
- Suppress matchup halos that require showing both sides.

If both are off:

- Draw no Attuned combat HUD.

## Journal Lore Expansion

The Attunement Journal should gain pages for the new lore and mechanics. Suggested page titles:

1. **The Altar of Reweaving**: Some altars do not deepen the soul's room; they turn an old pattern until it catches the light differently.
2. **What Reweaving Is Not**: Reweaving does not make a Focus louder, stronger, or freer; it changes the way a bound pattern is remembered.
3. **Altar Memory**: Every altar keeps a trace of what was last bound there, and Reweaving listens to that trace before it answers.
4. **The Radiant**: The Radiant keep vows in light, believing power is safest when named, witnessed, and remembered.
5. **The Unseen**: The Unseen keep masks and hidden roads, believing power is safest when no one can claim or own it.
6. **Witness And Veil**: Radiant and Unseen oppose each other like lantern and shadow, but both exist to keep attunement from becoming careless.
7. **The Seafarers**: The Seafarers carry fragments, journals, and half-remembered routes from shore to shore, trading in guidance rather than conquest.
8. **Tide Records**: A Seafarer's best map is not of land but return: where a wreck sank, where a bell was heard, where a shard washed clean.
9. **Reading The HUD**: The marks near your hand are not commands from the altar; they are the shape your active Foci make when the world pushes back.

The journal copy should stay short enough for the existing custom codex layout. If page count grows too far, split chapters cleanly rather than overflowing text.

## Journal Button Rendering Fix

The current journal page flipping works, but the clickable controls are not properly rendered. The likely root cause is visual, not functional: `AttunementJournalScreen` uses stock `Button.builder(...)` widgets on top of a fully custom generated journal texture.

Fix approach:

- Add a private journal-specific button subclass inside `AttunementJournalScreen`.
- Override `extractContents(GuiGraphicsExtractor, int, int, float)`.
- Use this custom button for chapter navigation and previous/next page controls.
- Render chapter buttons as slim codex tabs that sit over the baked book wells.
- Render previous/next as compact codex buttons.
- Keep hover, disabled, and selected states clear.
- Move or constrain the progress text/bar so it does not sit under the page buttons.
- Convert `"Previous"` and `"Next"` literals to translatable lang keys.

Tests should add source-level guardrails proving the journal uses custom-rendered buttons and keeps the custom screen path.

## Asset Targets

Final implementation assets should use these target paths where relevant:

- `src/main/resources/assets/attuned/textures/gui/altar_of_reweaving.png`
- `src/main/resources/assets/attuned/textures/block/altar_of_reweaving_base.png`
- `src/main/resources/assets/attuned/textures/block/altar_of_reweaving_top.png`
- `src/main/resources/assets/attuned/textures/block/altar_of_reweaving_gem.png`
- `src/main/resources/assets/attuned/textures/gui/sprites/faction/seafarers_mark.png`
- `src/main/resources/assets/attuned/textures/item/linecast_focus.png`
- `src/main/resources/assets/attuned/textures/item/netmender_focus.png`
- `src/main/resources/assets/attuned/textures/item/harborlight_focus.png`
- `src/main/resources/assets/attuned/textures/item/driftglass_focus.png`

Animated Focus item textures should follow the existing `64x512` medallion sheet convention with matching `.mcmeta`. The GUI texture should keep dynamic text, numbers, slots, buttons, and bars code-driven.

## Testing

Implementation should include focused tests for:

- Altar of Reweaving recipe/data registration.
- Reweaving menu slot validation.
- Reweaving consumes exactly 3 Foci and 1 Attunement Shard Fragment.
- Reweaving never consumes inputs when output cannot fit.
- Reweaving avoids returning sacrificed Focus ids when alternatives exist.
- Seafarers Foci are neutral and have translated faction/item/effect text.
- Seafarers Foci do not add combat modifiers.
- Client HUD config defaults both toggles to true.
- Combat HUD suppresses own/enemy sections independently.
- Enemy HUD off also suppresses target-derived matchup hints.
- Journal screen uses custom-rendered buttons.
- Journal page count and lang keys stay in sync.

Manual verification should include:

- Open both altars in-game.
- Reweave with valid and invalid inputs.
- Toggle own HUD and enemy HUD independently from Controls.
- Open the journal at multiple window sizes and confirm all buttons are visible, clickable, and not overlapping progress text.
- Fish with Seafarers Foci and verify no PvP-facing effects appear.

## Out Of Scope

- A full Mod Menu configuration screen.
- Guaranteed Focus selection.
- Focus rarity tiers.
- PvP-focused Seafarers mechanics.
- Replacing the existing Attunement Altar.
- Changing capacity binding rules.
- Implementing final code before this spec is reviewed.
