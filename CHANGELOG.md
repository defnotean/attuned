# Changelog

## Attuned 1.5.6+mc1.18.2 - Fabric Artifact Size Patch

### Changed
- **Fabric release naming** - Modrinth and CurseForge uploads now use Fabric: Minecraft 1.18.2 - Attuned 1.5.6+mc1.18.2 as the public version display name so future Forge and NeoForge files can sit beside Fabric builds without ambiguity.
- **Patch-only artifact refresh** - rebuilt the Minecraft 1.18.2 Fabric jar from the already-verified gameplay code with the release-size optimization applied; this patch does not add gameplay content or rebalance Foci.
- **Smaller release jar** - packaged jars now exclude source-only Blockbench mesh files, use the compact Ocean Relic runtime mesh texture, and keep RGBA-safe optimized public textures so the build stays readable while sitting around 7.4 MB instead of the previous roughly 10.6 MB footprint.

### Verification
- python tools\verify_repository.py
- python -m unittest discover -s tests
- ./gradlew build
- Jar content check confirmed the source Blockbench model/texture are absent and ocean_relic_trident_mesh.png is present.
## Attuned 1.5.5+mc1.18.2 - Gameplay Polish Backport

### Release scope and why the patch is large
- **This is a full gameplay-polish backport, not a small content-only patch.** The 1.18.2 maintenance line now carries the same player-facing systems added on the newer lines: Circles, party pings, shared contribution rules, richer preset metadata, Deep Lantern support Foci, action-bar routing, Updraft polish, combat feedback cleanup, expanded data-driven behavior palettes, docs, gallery updates, and the regression coverage needed to keep those systems shippable.
- **The roughly 14k-line increase is mostly real shipped surface area and tests.** The largest pieces are the server-authoritative Circle runtime, client snapshot/HUD state, Fabric networking payloads, preset import validation, setup metadata, new behavior-palette definitions, new Focus data/assets, example datapack entries, platform copy, and contract tests around every public boundary.
- **The added tests are intentional release weight.** Circles, shared credit, party assistance, preset import, build metadata, action-bar priority, Deep Lantern content, block-context scans, Luck stacking, damage formulas, and 1.18.2 API compatibility all gained focused checks so the backport is not relying on manual playtesting alone.
- **The docs and gallery changes explain the new surface area.** A patch with party systems, new support Foci, new authoring hooks, updated galleries, and a bigger Focus roster needs player-facing and author-facing documentation; otherwise the code would be present but difficult to understand, review, or publish cleanly.
- **The 1.18.2 compatibility layer adds lines without adding separate features.** This branch keeps the legacy Java 17/Fabric 1.18.2 APIs for HUD mixins, registry access, item NBT, Fabric packet callbacks, `FriendlyByteBuf` payload serialization, old attribute modifier operations, and client rendering while preserving the same gameplay behavior players see on newer branches.
- **Water movement needed a branch-specific fallback.** Minecraft 1.18.2 does not have the newer `minecraft:water_movement_efficiency` attribute, so Wellspring and Current Runner use underwater Dolphin's Grace palette behavior on this line to preserve their swim identity without shipping broken modifiers.

### Added
- **Deep Lanterns faction** - four exploration-support Foci bring the shipped roster to 99 Foci and give cave expeditions a support identity that is not another raw damage lane.
  - **Cavewick Focus** is a two-cost utility Focus that rewards player-built routes: placed lanterns and soul lanterns in a small radius keep gentle Night Vision refreshed, making caves readable without granting global tracking or structure search.
  - **Glowline Focus** is a three-cost route-following Focus that points toward recent same-dimension Circle pings. It uses only server-accepted party markers, so it is a coordination aid rather than a live locator.
  - **Rescueflame Focus** is a four-cost Holy support Focus that periodically assists a nearby drowning Circle member with Water Breathing. It deliberately excludes the wearer and uses Circle eligibility/cooldown rules so it behaves like rescue support, not a personal underwater buff.
  - **Depthglass Focus** is a three-cost navigation Focus that reads a held lodestone compass target and gives restrained same-dimension hints. It works from an explicit vanilla target the player already owns.
- **Server-authoritative Circles** - temporary expedition parties now support create, invite, accept, leave, disband, kick, invite expiry, capacity checks, cooldowns, disconnect cleanup, and snapshot syncing.
- **Party HUD and invite prompts** - clients receive Circle snapshots, invite prompts, and recent ping notices through dedicated payloads without exposing hidden inventory, private cooldowns, or item sharing.
- **Circle pings and navigation targets** - pings become server-accepted navigation targets with membership, dimension, visibility, loaded-target, range, and rate-limit checks. Glowline consumes those stored markers instead of scanning the world.
- **Shared contribution windows** - nearby same-dimension Circle members can receive eligible shared progress from combat, blocking, reveal, and helper actions without allowing passive proximity to farm progression.
- **Party-aware Pact Trial support** - eligible Circle contributors can share narrow trial progress while solo play remains complete.
- **Expanded data-driven Focus behavior palette** - new passive behavior types cover block-context effects, navigation hints, party assists, item-use windows, and marked targets:
  - `attuned:block_context_effect` keeps an effect refreshed near tagged local blocks, used by Cavewick for lantern routes.
  - `attuned:navigation_hint` gives restrained feedback toward accepted stored targets, used by Glowline and Depthglass.
  - `attuned:party_assist` helps an eligible Circle member with a small effect on cooldown, used by Rescueflame.
  - `attuned:use_item_window` grants a short effect or modifier after using a matching item or item tag.
  - `attuned:marked_target` lets a charged hit prime a short-lived mark and a later charged hit consume it for an effect.
- **Build setup metadata** - shared builds can carry sanitized role, note, party-size, required-Focus, and version-context metadata as advice only, without bypassing ownership or attunement rules.
- **Import validation and setup suggestions** - imported build codes are checked against the server Focus registry before saving; malformed names, slots, and metadata are rejected or downgraded to warnings.
- **Action-bar message priority gate** - repeated cooldown, apply, party, and combat feedback now route through shared action-bar helpers so important messages are less likely to be overwritten by low-priority spam.
- **Gameplay polish QA checklist** - added a manual release checklist for combat feel, Pact loops, Confluences, Resonant Surges, Circles, Updraft flight, onboarding, and journal clarity.

### Changed
- **Minecraft 1.18.2 gameplay parity** - this maintenance line now carries the same gameplay-polish systems as `latest` while preserving the Minecraft 1.18.2 Fabric target, Java 17 runtime, legacy packet callbacks, and dependency range.
- **Updraft release carried forward** - this line keeps the 1.5.1 Updraft Focus, smoother boost/brake controls, flight feedback, and PvP exhaustion safeguard.
- **Deep Lanterns documentation and journal pages** - the Attunement Journal and reference docs now explain Circles, public attunement state, shared credit, party pings, the Deep Lanterns faction, and the new behavior-palette entries.
- **Example datapack expanded** - the sample pack now covers build marks, canopy steps, rescue support, route windows, and navigation-hint patterns so datapack authors can copy working JSON.
- **Gallery coverage refreshed** - Modrinth/CurseForge gallery sheets were updated so the current Focus roster, neutral sets, Holy/Forge/Umbral coverage, and shipped item art remain visible after the new Foci landed.
- **Release jar footprint reduced** - release builds now keep full-size Blockbench source files out of packaged jars, ship a 512px Ocean Relic runtime mesh texture, and use RGBA-safe PNG optimization for generated public textures. This keeps the art readable while cutting built jars from roughly 10.6 MB to about 7.4 MB on the current release lines.
- **Combat and support math centralized** - repeated damage/support logic now flows through shared helpers, including clearer direct-combat target checks and capped positive Luck stacking.
- **Pact and resonance feedback tightened** - Pact deaths, tactical feedback, trial progress messaging, combat polish hooks, and resonance/surge feedback now share more of the same routing and validation paths.
- **Reliquary/build workflows hardened** - preset save, import, apply, delete, metadata inference, and quick-apply paths now share stricter validation so stale UI state or malformed imported data cannot silently mutate the wrong build.

### Fixed
- Circle membership changes now clean up contribution windows and sync updated snapshots so clients do not keep stale party rows after leaving, kicking, disbanding, or disconnecting.
- Friendly or invalid targets are filtered out of party-assist, hostile-only Focus, Pact, Apex, and contribution checks so Circle members are not treated as enemies just because they are nearby.
- Build-share imports reject malformed metadata, non-string slot ids, component-like names, and unknown required Focus ids before saving the preset.
- Positive Luck modifiers are capped through shared logic, preventing stacked support effects from turning fishing and loot-adjacent bonuses into unbounded values.
- Deep Lantern support effects use bounded cadences and same-dimension checks so navigation and rescue feedback remain readable and cannot become global trackers.

### Internal
- **Large patch accounting** - the added lines are tied to shipped behavior, release documentation, generated/public assets, data definitions, and regression coverage rather than unrelated churn.
- Added contract tests for Circle policy, Circle manager behavior, pings, snapshots, invite prompts, party HUD geometry, shared contribution credit, party effects, action-bar routing, preset metadata/import validation, damage formula helpers, Luck stacking, Deep Lantern content, and block-context scans.
- Expanded repository validation around generated and authored repository structure, source-marker hygiene, Focus definitions, behavior palettes, docs claims, platform gallery assets, release-facing feature counts, Python syntax, and transient-cache leaks.
- CI now runs the broader repository and Python checks alongside Gradle build/test gates before release artifacts are accepted.
- Added Circle and party config knobs for max members, invite TTL/cooldowns, shared-credit radius/window, same-dimension behavior, party effects, party HUD, confluence hints, and setup suggestions.
- Release-facing docs now keep only the files needed for the public build while still documenting the shipped assets, public behavior contracts, and authoring workflows.

### Compatibility and migration notes
- Existing worlds do not need a data migration for Circles. Circle state is transient server runtime state, not permanent item ownership.
- Solo play remains complete. Circle systems add coordination, public role hints, and eligible shared credit, but they do not replace solo Pact, Focus, or Confluence progression.
- Author datapacks using older Focus behavior palette entries continue to work; the new behavior types are additive.
- This maintenance release targets Minecraft 1.18.2, Java 17, Fabric Loader 0.18.4+, and Fabric API 0.77.0+1.18.2; newer Minecraft release lines carry the same gameplay systems through their own compatibility branches.

## Attuned 1.5.4+mc1.18.2 - Compatibility Fixes

### Fixed
- Restored the inventory Focus panel on survival and creative inventory screens for Minecraft 1.18.2.
- Widened Fabric Loader and Fabric API dependency metadata to the compatibility floor for this Minecraft line so older valid Fabric installs are not rejected unnecessarily.

### Internal
- Updated the Fabric dependency metadata contract for the widened runtime range.

## Attuned 1.5.3+mc1.18.2 - Fabric Compatibility Hotfix

### Fixed
- Lowered the Fabric Loader dependency metadata to the verified 0.18.4 runtime floor so common Fabric installs are not rejected by an unnecessary 0.19.3 requirement.

### Internal
- Added a Fabric dependency metadata contract test so future maintenance builds keep published Loader/Fabric API floors aligned with verified runtime compatibility.

## Attuned 1.5.2+mc1.18.2 - Minecraft 1.18.2 Maintenance Build

### Changed
- Published a dedicated Minecraft 1.18.2 maintenance artifact using the verified compatibility branch.
- Carries forward Attuned 1.5.1 Updraft Flight Polish and the 1.5.0 eightfold affinity, Pact, Apex, Confluence, and 95-Focus roster.
- Release metadata targets Minecraft 1.18.2, Java 17, Fabric Loader 0.19.3, and Fabric API 0.77.0+1.18.2.

## Attuned 1.5.1 - Updraft Flight Polish

### Added
- **Updraft Focus** - a new elytra utility Focus rounds the shipped roster to 95 Foci. Hold jump while fall-flying to boost forward with smooth server/client motion, and hold sprint/control to brake hard without needing to aim upward or downward.
- **Updraft flight feedback** - boost, brake, and exhaustion states now use restrained vanilla particles, sounds, and action-bar feedback so flight has readable feel without adding new art dependencies.

### Changed
- **Smoother Updraft controls** - flight thrust and braking now ease velocity instead of snapping it, making long glides less jittery and easier to steer.
- **PvP exhaustion safeguard** - sustained PvP pressure for more than five seconds causes Updraft to falter briefly, applying a strong brake plus short Weakness and Slowness so the Focus cannot be abused as endless combat disengage.

## Attuned 1.5.0 - Eightfold Affinities & Resonant Engagement

### Added — Eightfold Affinities
- **Eight first-class Affinities** - the four counter-combat affinities have been promoted into the full eight-value Wheel of Refusals: Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, and Umbral are now all first-class identities (stance, Discord, Pact, and Apex) rather than the older four affinities plus a separate Aspect layer. Each affinity is strong against two others and weak to two reciprocal others, and the historic four-cycle survives as a subset of the expanded matrix.
- **Four new Affinity Pacts** - Tide, Forge, Verdant, and Umbral each gain their own Pact (with awakening advancement and death message) alongside the original four, so committing to any single affinity now wakes a matching Pact.
- **Four new Apex capstones** - committing an Apex build to a promoted affinity now grants a modest, matchup-aware on-hit capstone instead of resolving to nothing: **Riptide** (Tide) drags landed apex-melee foes into the current with Slowness, **Crucible** (Forge) sears them with forge-heat (sets them on fire), **Bloomward** (Verdant) returns a little life to you on the hit, and **Gloaming** (Umbral) saps a foe's strength with Weakness. Each effect scales with the matchup (longer/stronger when your affinity beats the foe's, and does not proc when the foe's affinity counters yours), and these capstones own no identity ability or dodge.
- **16 pure-affinity modifier Foci** - the first 1.5.0 balance batch fills out Fury, Bastion, Tide, Forge, Verdant, and utility mobility lanes with readable medallion art and simple stat identities, bringing the shipped roster to 94 Foci.
- **Custom Focus visual motifs** - Softstep, Aegis, Tide, and Cinder now emit subtle vanilla-particle flourishes on the existing aura cadence, keeping the visuals readable without adding new gameplay state.
- **Resonant combo MVP** - Softstep + Needle now rewards a sneaky opener with a brief Weakness window, action-bar feedback, and restrained particles/sound.
- **Five new Confluences** - Iron Bastion, Razor Tempo, Sparkbrand, Thornbloom, and Tidal Engine add more pure-affinity build payoffs and hidden discovery advancements.
- **Creative inventory organization** - the 94-Focus roster is split into Fury & Bastion, Zephyr & Holy, Tide & Forge, Verdant & Umbral, and Utility & Tools tabs with stable affinity/family/cost ordering.

### Added — Resonant Engagement
- Pact Trials (Tier 4) - all 9 pacts, permanent unlocks, journal progress
- Pact tacticals on Focus Ability key when resonance >= 50%
- Pact tactical overcharge: crouch + 0.25 resonance spend for amplified tactical
- New active abilities: Veil, Mask, Pearlguard, Sparkweld, Oathguard
- Affinity Loom at Altar of Reweaving (1 Focus + escalating shards → same-affinity reroll)
- Build sharing via Reliquary clipboard (attuned:v1: codes)
- Faction set bonuses: Tideborn, Forgebound, Wildroot, Umbral
- Combat tuning in config (discord 1.20, resonance rates, advantage multipliers)
- Resonant surge broadcasts, mob pressure, discord half-rate
- Onboarding hints (resonance armed, ability, confluence, pact trial complete)
- HUD: apex pulse, charged melee dot, trial pip, tempered tick, confluence pulse, pact tactical cooldown ring
- Journal: pact trial page, tempering page
- Combat feedback: resonance gain/drain, kill streaks, surge charge, ability casts, Apex procs (Execute/Judgment), pact tacticals

### Changed
- **Balance note - migrated Foci changed affinity** - the 12 Foci that previously carried only an Aspect now carry a first-class Affinity, so existing builds that used them may resolve differently than before. A loadout that was affinity-neutral (or read as Discord/single-affinity in a particular way) because those Foci had no affinity can now commit to, or diversify across, the promoted affinities — which can change your committed stance, whether you read as Discord, which Pact wakes, and which Apex capstone (if any) your build resolves to. Review builds that lean on the migrated Tide/Forge/Verdant/Umbral Foci.
- Discord damage softened to 1.20× (configurable)
- Resonance mid-fight fill slightly faster (0.012 per damage)
- Pact trial goals tuned for solo pacing: Pyresworn 40 ignites, Stoneheart 400 absorbed damage, Windrunner 6,400 sprint blocks, Radiant Covenant 25 reveals, Tidesworn 40 slows, Forgebound 25 ignites, Wildroot 36,000 growth ticks (~30 minutes while awake), Nightsworn 150 absorbed damage, Untethered 20 apex kills
- Pact trial engagement gates: Stoneheart while blocking; Forgebound/Untethered near hostiles (16 blocks); Wildroot Regen full / near-hostiles half / idle none; Windrunner near hostiles or at Apex

### Internal
- BuildShareCodec, PactTrials, PactTier4, PactTacticals, CombatFeedback, contract tests

## Attuned 1.4.1 - Journal Clarity

### Fixed
- Focus item descriptions and tooltips now show only the Focus-specific readout plus affinity identity; they no longer list explicit affinity matchup details.
- The Attunement Journal now contains the full affinity matchup reference, including every affinity's two strengths and two weaknesses.

### Changed
- Release notes, platform descriptions, and authoring docs now direct players to the Attunement Journal for the who-counters-who reference instead of individual Focus descriptions.

## Attuned 1.4.0 - Resonant Depths

### Added
- **Aspect counter wheel** - Foci can now declare an `aspect` separate from their old affinity. Tooltips show the Focus's affinity identity, while the Attunement Journal owns the full Wheel of Refusals matchup reference. The first batch adds 12 new Tide, Forge, Verdant, and Umbral Foci with matching animated medallion art.
- **Journal-owned Aspect reference** - Aspect-bearing Foci no longer list matchup details in item descriptions. The Attunement Journal now explains which Aspects beat or answer each other in one place.
- **Tide / Forge / Verdant / Umbral Focus batch** - 12 new real Foci ship as registered items, data definitions, models, language, behavior-palette entries, and animated textures: Undertow, Riptide Heart, Pearlguard, Slagbrand, Anvilheart, Sparkweld, Thornwake, Seedcall, Bramblegate, Nullveil, Cinderthief, and Snaremoon.
- **Aspect Focus art pass** - the new Aspect Foci ship with polished 64x512 animated Minecraft item sheets, stronger medallion silhouettes, and inventory-scale readability checks.
- **Attunement Sanctum** - a small, hand-built jigsaw shrine (one 15x8x15 piece of polished deepslate, amethyst pillars, and a chiseled-deepslate altar) that generates rarely in lush caves, forests, and dark forests. Its altar chest rolls two light- and depth-themed Foci plus a few Attunement Shard Fragments from a dedicated `attuned:chests/sanctum` table. `/locate structure attuned:attunement_sanctum` finds the nearest one.
- **Resonant surges** - during a thunderstorm a resonance surge can ignite near a random online player: for about a minute, anyone standing within its radius builds resonance four times as fast. It is deliberately loud (a spark column and an ambient boom every second), so the fast fill comes with the risk of being a beacon in the storm. One surge is live at a time; tune the cadence, length, and radius with `surge_interval_ticks`, `surge_duration_ticks`, and `surge_radius`.
- **Apex identity abilities** - the two affinity-less Apex paths now answer the Focus Ability key when no awake ability Focus is equipped. While armed, **Maelstrom** erupts a chaos nova (knocks nearby hostiles and PvP-eligible players away and Weakens them) and **Stillpoint** spreads a tranquility field (nearby hostile monsters lose their target and are briefly slowed), each on a 30-second cooldown. The affinity capstones keep no active ability.
- **Faction set bonuses** - running three or more ACTIVE Foci of the same faction now grants a small, free passive perk for that faction: Unseen gives Speed while sneaking, Seafarers gives Luck near water, Offshore gives Water Breathing while submerged, Radiant gives a brief Regeneration in bright light, Reliquary gives Luck, Verdant Choir restores a little hunger on grass, Ashen Forge gives Fire Resistance near a lit forge or lava, and Revenant slows nearby undead. Drop below three active Foci and the bonus stops.
- **Focus tempering** - the Altar of Reweaving can now fuse two copies of the same untempered Focus (plus a Shard Fragment catalyst) into one **Tempered** copy: every attribute modifier is strengthened by +25% and the Focus costs +1 attunement, with dormancy and the capacity readout accounting for the surcharge automatically. A Tempered Focus shows a gold name and a Tempered tooltip line, and cannot be tempered again. Reweaving three different Foci behaves exactly as before.
- **Affinity inspect** - crouch and hold your crosshair on another player for ~1.5 seconds to read their public stance (committed affinity / Discord / Neutral, plus Apex capstone and whether it is armed) on your action bar. Server-mediated, range- and line-of-sight-limited to 24 blocks, and rate-limited per onlooker.
- **Reliquary build previews** - hovering a saved build in the Focus Reliquary now shows its six Foci as item icons, greying any whose Focus you cannot currently source for an Apply (pooled across your equipped slots, the reliquary, and your inventory).
- **Grand Focus Reliquary** - a second-tier reliquary with a 54-slot (9x6) grid, twice the storage of the Focus Reliquary. It shares the same screen, equipped-slot column, build saving/applying, build previews, and quick-swap as the satchel, and is crafted by ringing an existing Focus Reliquary with leather around an amethyst block. Neither reliquary can be stored inside the other.
- **Focus Confluences** - small set bonuses that wake when a specific 2-3 Focus combination is all active at once, at no attunement cost. Waking one announces in chat; the first time you discover a given Confluence it plays a fanfare and records a hidden advancement. Ships four: **Immovable** (Anchor + Rivet, +knockback resistance), **Bulwark of Light** (Votive + Oathguard, +armor), **Hunter's Patience** (Lantern + Veil, +movement speed), and **Tempest** (Rainstep + Stormcall, +attack damage).
- **Conditional Confluences** - three more Confluences whose payoff is a context-sensitive buff kept refreshed while active: **Wildward** (Mossheart + Rootstep, Resistance I on natural ground), **Sunwarden** (Votive + Bellwether, Regeneration I in bright light), and **Forgewarded** (Kilnward + Emberward, Fire Resistance near a lit forge, magma, or lava).
- **Confluences journal chapter** - a discovery collection: found Confluences show their name and effect, the rest read `??? - undiscovered (N Foci)` until you wake them.
- **Confluence feedback** - a pip per active Confluence on the Foci HUD, plus a Focus-panel tooltip hint when you are one Focus short of a Confluence you have already discovered.
- **Datapack Confluences** - packs can define their own at `data/<namespace>/attuned/synergy/<id>.json` (members + modifiers + optional behavior).
- **Behavior palette** - datapacks can now define some Focus behaviors as data instead of Java, via the synced `data/<ns>/attuned/focus_behavior/<id>.json` registry. The first palette type, `attuned:conditional_mob_effect`, keeps a mob effect refreshed while a composable condition (`in_rain`, `underwater`, `low_light`, `sneaking`, `on_block_tag`, `in_biome_tag`) holds. A Focus's `behavior` id now resolves code-first-then-data, so palette entries and the shipped code behaviors never collide. See `docs/reference.md`.
- **Custom Focus item pool** - eight blank, resource-pack-skinnable Focus items (`attuned:custom_focus_1` through `custom_focus_8`) so datapack authors can point a `focus/<name>.json` at one and skin its name, lore, art, and model with a resource pack, getting a bespoke Focus identity without a JAR. They ship a neutral default name and texture (so they are never raw keys) and carry no bundled definition, so the advertised Focus count is unchanged. See `docs/reference.md`.
- **Datapack Focus authoring** - `/attuned validate` now checks author packs file by file: every Focus's `item`, `behavior` (code or palette), and modifier `attribute` ids must resolve, the `focus_behavior` palette files are walked, and a missing display-name lang key is reported as a warning rather than a failure. Ships a worked example datapack (`docs/example-pack/`, three Foci across the attribute, shipped-behavior, and palette lanes) and an external-author walkthrough (`docs/authoring-foci.md`).
- **Behavior palette breadth** - three more passive palette types join `conditional_mob_effect`: `attuned:on_hit_effect` (apply an effect to the victim or self on a charged, hostile-only melee hit, reusing the same combat guards as the code Foci), `attuned:periodic_effect` (a flat mob effect refreshed on a fixed cadence), and `attuned:attribute_while` (an attribute modifier applied only while a condition holds). All are still passive (no Focus Ability) and validated by `/attuned validate`. See `docs/reference.md`.

### Fixed
- Dodged or invulnerability-frame hits no longer consume Ashen Debt, the Needle opener cooldown, or break Veil.
- Thornward reflect and Leech lifesteal now clamp the hit to the victim's health pool before scaling, so an Apex Execute kill can no longer reflect or heal tens of thousands of damage in PvP.
- Lengthened Night Vision refresh windows for Nightgaze and Harborlight so the client no longer constantly flickers, and fixed Stormcall's rain check so lightning can trigger while sprinting in open rain.
- Tremor now outlines the whole connected ore vein instead of a single block (stone and deepslate variants trace as one vein, mined blocks drop off the outline individually), and triggers from blackstone, basalt, and calcite so ancient-debris tunnels and geodes reveal too.

### Changed
- **Focus art direction** - new Focus art now follows a stronger medallion/talisman style guide with contact-sheet review and inventory-scale QA so future Foci match the existing Attuned theme.
- **Umbral Eclipse texture polish** - refreshed Gloomstride, Duskward, Shadowmeld, Dreadfang, and Eclipse so the shadow set reads as chunkier medallion-style Focus art beside the older library.
- Rebuilt the showcase 3D models: the Ocean Relic Trident gets a hand-authored silhouette with tapered tines, barbed prongs, glow fins, and kelp ribbons; the Frostbound Trident graduates from a flat sprite to a full icy voxel model; and both the Attunement Altar and Altar of Reweaving get far richer multi-element geometry (floating gems, rune panels, loom arch).
- **Attunement Journal redesign** - lighter parchment with dark ink (replacing light text on tan), a flat chapter rail with affinity-colored dots and a highlighted current chapter, and a regenerated panel texture. Each chapter is now one continuous, mouse-wheel-scrollable document with a scrollbar; content no longer truncates, and Previous/Next move between chapters.

### Internal
- Added contract coverage for the Aspect counter matrix, 12 shipped Aspect Foci, Aspect tooltip strings, asset QA outputs, and behavior-palette documentation so release checks catch missing content before publishing.
- Added a server-side attunement resolution cache keyed by player, immutable inventory identity, and current capacity.
- Shared one cached client attunement readout snapshot across the Focus panel, Foci HUD, Combat HUD, and Attunement Altar render passes.
- Added the Confluences engine: a pure `SynergyResolver`, the `Synergies` server-tick runtime, a `SynergyDefinition` datapack registry (`SYNERGY_DEFINITIONS`), and a `DISCOVERED_CONFLUENCES` player attachment.
- Attunement Journal chapters now own their pages directly, removing the hand-maintained page-index lists and the class-load drift guard.
- Added `tools/preview_journal.py`, an offline journal-layout harness that composites the real texture with the screen's exact layout math.
- Trimmed combat hot paths: the Lodestone item-pull query now runs every other tick, and idle Resonance decay batches into one synced write every 20 ticks (same rest-decay curve) instead of writing the attachment every tick.

## Attuned 1.3.1 - 26.1.2 Launch Hotfix

### Fixed
- Fixed the Minecraft 26.1.2 launch crash from `PlayerAttackMixin` by anchoring the melee-charge snapshot at `Player.attack` method entry instead of the removed `resetAttackStrengthTicker` invocation.

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
