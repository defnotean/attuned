## Attuned 1.6.0 - Resonant Engagement

### Added
- **Pact Trials (Tier 4)** - each of the nine Pacts now tracks a long-term trial while that Pact is awake. Completing a trial is permanent, records in the Attunement Journal, and unlocks a small Tier 4 bonus the next time you run that Pact.
- **Pact tacticals** - when resonance is at least 50%, the Focus Ability key fires your awake Pact's tactical instead of a Focus ability. At apex resonance the tactical is stronger and the HUD apex pulse lights up so you know the window is live. **Crouch** to **overcharge** a tactical (spends 0.25 resonance for amplified effects).
- **New active abilities** - **Veil**, **Mask**, **Pearlguard**, **Sparkweld**, and **Oathguard** join the roster as real Focus Ability Foci with cooldowns, particles, and journal-readable identities.
- **Affinity Loom** - the Altar of Reweaving can reroll a single Focus into another Focus of the same affinity: sacrifice one Focus plus escalating Attunement Shards (cost rises with each loom use) and receive a same-affinity replacement from the reweaving pool.
- **Build sharing** - copy a saved Reliquary build to the clipboard as an `attuned:v1:` code and paste it on another satchel to import the same six-slot layout (names, tempered state, and slot order).
- **Faction set bonuses** - **Tideborn**, **Forgebound**, **Wildroot**, and **Umbral** factions now grant small passives when three or more of their Foci are active, alongside the existing faction perks from 1.4.
- **Combat tuning config** - Discord damage multiplier (default 1.20×), resonance fill per damage (default 0.012), and advantage/disadvantage multipliers are exposed in `config/attuned.json` for pack authors.
- **Resonant surge polish** - thunderstorm surges now broadcast to nearby players, apply light mob pressure near the anchor, and grant Discord builds only half the surge resonance rate so mixed kits do not out-scale committed affinities during storms.
- **Onboarding hints** - first-time action-bar guidance for resonance armed, Focus Ability use, waking a Confluence, and completing a Pact Trial.
- **Combat feedback** - particles and sounds on resonance gain/drain, kill streaks, surge charge, ability casts, Apex capstone procs (including Execute and Judgment), and pact tactical use.
- **HUD and journal** - apex pulse, charged-melee readiness dot, pact-trial progress pip, tempered-Focus tick, confluence pulse, and pact-tactical cooldown ring on the Foci HUD; new journal chapters for Pact Trials and Focus tempering.

### Changed
- **Discord damage softened** - the default Discord stance damage bonus is now 1.20× instead of the harsher 1.33× curve (still configurable).
- **Faster mid-fight resonance** - dealing damage fills resonance slightly faster (0.012 per point of damage by default) so apex windows arrive more often in sustained fights.
- **Pact trial pacing** - trial goals are sized for solo play: Pyresworn 40 ignites, Stoneheart 400 absorbed damage, Windrunner 6,400 sprint blocks, Radiant Covenant 25 reveals, Tidesworn 40 slows, Forgebound 25 ignites, Wildroot ~30 minutes while awake, Nightsworn 150 absorbed damage, Untethered 20 kills at apex resonance.
- **Pact trial engagement** - several trials now require combat context within a 16-block radius (or Apex for Windrunner/Untethered): Stoneheart only accrues while **blocking**; Forgebound ignites and Untethered apex kills need nearby hostiles; Wildroot accrues at full rate with Regeneration, half rate near hostiles, and not at all in idle safe zones; Windrunner sprint blocks need nearby hostiles unless you are at Apex.
