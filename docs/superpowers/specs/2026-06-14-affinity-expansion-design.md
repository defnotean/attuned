# Attuned Affinity Expansion Design Plan

> **For Hermes:** Use test-driven-development for each code task and keep the user's counter-system preference central: every new Affinity must counter others and be countered by others.

**Goal:** Expand Attuned beyond the current four first-class `Affinity` values without losing readable counterplay, Pact identity, Discord behavior, or journal clarity.

**Current architecture:** `Affinity` is currently the load-bearing 4-value stance/Pact/Discord system: Fury, Bastion, Zephyr, Holy. `Aspect` is already the broader 8-value counter identity: Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, Umbral. Many shipped Focus JSON files now carry both `affinity` and `aspect`.

**Key constraint:** Do not add standalone flavor buckets. Every expanded Affinity must explicitly define what it counters, what counters it, how players see that relationship, and how it affects builds.

---

## Current State Evidence

- `src/main/java/dev/attuned/api/focus/Affinity.java`
  - exactly 4 enum values.
  - `beats(Affinity)` is a 4-cycle: Holy > Fury > Bastion > Zephyr > Holy.
  - `argb()` is a 4-case switch.
- `src/main/java/dev/attuned/api/focus/Aspect.java`
  - already ships 8 counter identities.
  - every Aspect has exactly two strengths and reciprocal weaknesses.
- `src/main/java/dev/attuned/api/focus/FocusDefinition.java`
  - supports optional `affinity` and optional `aspect` separately.
- `src/main/java/dev/attuned/pacts/Pact.java`
  - one Pact per current Affinity plus Untethered.
  - `Pact.ofAffinity(Affinity)` is a 4-case switch.
- `src/main/java/dev/attuned/combat/CombatContext.java`
  - caches active `Affinity` counts for Pacts/Apex/combat.
- Existing 1.4.x content already uses the new identities through `aspect`, not expanded `affinity`.

---

## Architecture Decision Gate

The user asked for “significantly more Affinities.” There are three viable meanings:

### Option A — Promote the existing 8-Aspect wheel into first-class Affinities

This is the most direct interpretation and the recommended first implementation if the goal is true mechanical Affinity expansion.

Roster:

1. Fury
2. Bastion
3. Zephyr
4. Holy
5. Tide
6. Forge
7. Verdant
8. Umbral

Counter rule:

- Use the existing `Aspect` matrix as the initial `Affinity` matrix.
- Every Affinity has exactly two strengths and two reciprocal weaknesses.

Implementation consequences:

- `Affinity` becomes the canonical 8-value counter wheel.
- Existing `Aspect` can become either:
  - a backwards-compatible alias/deprecated data field for old JSONs, or
  - a removed layer after all JSON/docs migrate.
- Current Aspect Foci should likely have their `affinity` changed to their identity Affinity, e.g. `undertow_focus` becomes `affinity: "tide"` instead of `affinity: "zephyr"` plus `aspect: "attuned:tide"`.
- New Pacts are needed for Tide/Forge/Verdant/Umbral, or Pacts must be explicitly limited to “core Affinities only.”
- Untethered/Discord rules need redesign because “one of every Affinity” becomes unrealistic with 8 values.

### Option B — Keep Affinity as the 4 Pact stance system; expand Aspects and present them as player-facing Affinities

This is lowest-risk technically but does not truly expand the Java `Affinity` enum.

Implementation consequences:

- Rename/explain the user-facing language so Aspects are “Affinities” in journal/UI copy.
- Keep code’s `Affinity` as a hidden/legacy stance layer.
- Add more `Aspect` values beyond 8.
- Avoid breaking Pacts/Discord/Apex.

### Option C — Full 12+ Affinity redesign

This is the broadest option and should be a major version feature.

Implementation consequences:

- Design a new 12+ counter graph from scratch.
- Requires new names, colors, lore, Pacts, Focus content, HUD visuals, journal pages, tests, and likely balance passes.
- Should not start until the roster is approved.

---

## Recommended Path

Proceed with **Option A: 8 first-class Affinities** as the next implementation phase, unless the user wants a 12+ redesign immediately.

Why:

- It satisfies “significantly more” by doubling the first-class Affinity roster.
- The matrix is already designed, tested, documented, and partially represented in shipped content.
- It converts existing work into a cleaner system instead of keeping two overlapping identity concepts.

---

## Option A Implementation Plan

### Task 1: Lock the 8-Affinity matrix with failing tests

**Files:**

- Modify: `src/test/java/dev/attuned/api/focus/AffinityCounterSystemTest.java` or create it if absent.
- Modify existing tests that assert the old 4-cycle only.

**Assertions:**

- `Affinity.values()` is exactly Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, Umbral.
- Each Affinity has exactly two strengths.
- Each Affinity has exactly two reciprocal weaknesses.
- `beats()` follows the former Aspect matrix.
- `argb()` has a non-neutral color for every Affinity.

**Expected RED:** tests fail because `Affinity` only has four values.

### Task 2: Expand `Affinity` enum

**Files:**

- Modify: `src/main/java/dev/attuned/api/focus/Affinity.java`

**Implementation:**

- Add `TIDE`, `FORGE`, `VERDANT`, `UMBRAL`.
- Replace one-target `beats()` switch with `strongAgainst()` / `weakAgainst()` pattern matching `Aspect`.
- Add colors for the new Affinities.
- Keep bare string codec names stable: `tide`, `forge`, `verdant`, `umbral`.

### Task 3: Decide the `Aspect` compatibility layer

**Files:**

- Modify: `src/main/java/dev/attuned/api/focus/Aspect.java`
- Modify: `src/main/java/dev/attuned/api/focus/FocusDefinition.java`
- Modify tests under `src/test/java/dev/attuned/api/focus/`

**Recommended compatibility behavior:**

- Keep `aspect` in `FocusDefinition` temporarily for old datapacks.
- Add a helper like `FocusDefinition.identityAffinity()` that returns:
  1. explicit `affinity` if it is one of the expanded identity Affinities, or
  2. the mapped `aspect` as fallback during migration.
- Mark journal/docs to use Affinity language only after JSON migration.

### Task 4: Migrate shipped Focus JSON

**Files:**

- Modify: `src/main/resources/data/attuned/attuned/focus/*_focus.json`

**Implementation:**

- For Foci currently carrying `aspect: "attuned:tide"`, set `affinity: "tide"`.
- For Forge, set `affinity: "forge"`.
- For Verdant, set `affinity: "verdant"`.
- For Umbral, set `affinity: "umbral"`.
- Remove `aspect` if `Affinity` is now the identity source, or keep it for one transition release if tests/docs expect it.

### Task 5: Update Pacts deliberately

**Files:**

- Modify: `src/main/java/dev/attuned/pacts/Pact.java`
- Modify: Pact tests and `en_us.json` Pact translations.

**Open design decision:**

Choose one:

1. Add four new 3+ Affinity Pacts:
   - Tide: Undertow
   - Forge: Tempered
   - Verdant: Overgrowth
   - Umbral: Eclipse
2. Keep Pacts core-only and let new Affinities affect counters/build identity without Pact names.

Recommended: add the four new Pacts, but keep their mechanics modest at first.

### Task 6: Redesign Untethered / Discord rules

**Files:**

- Modify: `Pact.java`, `Pacts.java`, `Apex.java`, relevant tests.

**Problem:** current Untethered assumes all current Affinities are practical to carry. With 8 Affinities, “all Affinities” is too strict.

**Recommended new rules:**

- Discord remains: active build has 2+ distinct Affinities.
- Untethered becomes: 4+ distinct active Affinities with no single Affinity at 3+.
- Maelstrom Apex remains the high-Discord capstone but keys off diversity threshold, not all 8.

### Task 7: Update HUD, journal, tooltips, and docs

**Files:**

- Modify: `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java`
- Modify: `src/client/java/dev/attuned/client/AttunedTooltips.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`
- Modify: README/docs/reference as needed.

**Rules:**

- Focus descriptions/tooltips should still not list full who-counters-who details.
- The Attunement Journal owns the complete matchup reference.
- Tooltips can show the Affinity identity only.

### Task 8: Full verification

Run:

```bash
./gradlew test
./gradlew build
python tools/verify_repository.py; status=$?; echo VERIFY_EXIT:$status; exit $status
python -m unittest discover -s tests
uv run --with pytest --with pillow -m pytest tests/ -q
```

---

## Acceptance Criteria

- Players can equip and build around at least 8 first-class Affinities.
- Every Affinity has explicit strengths and weaknesses.
- Pacts/Discord/Apex behavior is documented and tested under the expanded roster.
- Journal contains the full matchup reference.
- Focus tooltips/descriptions do not include explicit matchup lists.
- Existing worlds/datapacks using the old 4 Affinities still load.
- Existing Aspect-tagged shipped content is migrated or gracefully supported.

---

## Recommended User Decision

Start with **Option A: promote the existing 8 Aspect identities into true Affinities**, then decide whether to add a later 12+ roster once the 8-Affinity migration is stable.
