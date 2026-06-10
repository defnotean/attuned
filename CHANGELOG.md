# Changelog

## Attuned 1.3.0 - The Focus Reliquary

### Added
- **Focus Reliquary** - a new craftable bag (leather wrapped around an amethyst shard) that stores spare Foci. Right-click it to open.
- **Equipped Focus management in the Reliquary** - the reliquary screen shows your six equipped Focus slots alongside the bag, so you can rebuild your loadout without juggling your inventory.
- **Move Foci however you like** - drag-and-drop, click-to-grab then click-to-drop, or shift-click to send a Focus between the reliquary, your equipped slots, and your inventory.
- **Builds (saved loadouts)** - type a name and save the Foci you currently have equipped as a "build". Keep up to nine, click a build to select it, then Apply to re-equip it instantly or Delete to remove it. Applying a build sources the Foci from your reliquary and inventory and tells you about any it could not find.

### Changed - Foci reworked to match their descriptions
- **Rootstep** - its movement and fall-damage help now applies only while standing on natural blocks.
- **Bloom** - now grants rare seeds, flowers, and honeycomb while gathering plants instead of a flat Luck bonus.
- **Rivet** - knockback resistance now applies only while grounded and braced (crouching, blocking, or standing on metal).
- **Mossheart** - hostile hits now grant brief Resistance while you stand on moss, grass, or leaves.
- **Kilnward** - hostile hits near lit furnaces, magma, or lava now grant brief Resistance instead of fire immunity or flat armor.
- **Temper** - using a forge block now briefly empowers your fully charged melee hits.
- **Mask** - crouching in low light now briefly resists reveal and Glowing effects.
- **Whisper** - now a neutral Focus-ability hush (80 ticks, 300-tick cooldown) that softens broken-sight mob detection.
- **Votive** - now grants a short absorption shield on a hostile hit while in bright light or near lit candles.
- **Oathguard** - absorption can now also trigger on a hostile hit, not just on blocking.
- **Bellwether** - now reveals nearby threats after a bell rings or while you stand within range of a bell.
- **Netmender** - now actually restores fishing-rod durability on a successful catch, on a cooldown.
- **Harborlight** - now works with a held lantern or a nearby placed lantern, near water in low light.
- **Blackout** - ability-key smoke pulse that briefly blinds nearby targeting mobs and drops their target.
- Marked non-stacking Foci (such as Bloom, Rootstep, and Rivet) as unique, so a duplicate stays dormant.
- Rewrote many Focus tooltips so the in-game text describes what the Focus actually does.

### Fixed
- Tightened GUI alignment for the Attunement Table, Reweaving Altar, and Attunement Journal.
- Corrected the Harborlight tooltip ("at night" -> "in low light") to match its light-level trigger.
- Fixed charged-hit combat checks so Sunlance, Temper, Pyresworn, and Radiant Covenant read the player's pre-reset melee charge instead of vanilla's post-hit reset value.
- Prevented Thornward reflected damage from re-entering player-attack proc pipelines such as Apex Execute, Pyresworn, Needle, and resonance credit.
- Preset application and Reliquary storage now preserve definitionless Foci as stored items instead of silently deleting them when a datapack definition is missing.
- Double-clicking Delete on a saved build now removes only the selected build instead of deleting the next shifted build too.

### Internal
- Added the reusable Focus holder, the satchel-contents data component, the reliquary menu/screen, the preset networking, and Minecraft-free resolver/cooldown tests behind the Reliquary.
- Expanded the test suite and reference docs, added an offline GUI preview/customizer, and added a pre-push check that blocks pushes whose committed tests depend on untracked files.

## Attuned 1.2.7 - Ocean Relic Trident release polish

- Polished the Ocean Relic Trident held, inventory, and thrown rendering paths.
- Added projectile render routing so thrown harpoons use the Ocean Relic projectile model.
- Hardened CI, repository validation, dependency locking, and runtime smoke coverage.

## Attuned 1.2.6 - Ocean Relic Trident hotfix

- Fixed the Ocean Relic Trident held pose so it sits in the player's hand instead of floating behind the arm.
- Fixed the throwing wind-up pose so the trident prongs face forward.
- Added regression coverage for the third-person hand anchor and throw-pose orientation.
