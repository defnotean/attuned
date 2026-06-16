# Attuned Maintenance Track Plan (runs parallel to 1.4)

> **Implementation:** Work tasks in order using the checkbox (`- [ ]`) syntax below.
>
> **The "READ THIS FIRST" constraints section of `docs/superpowers/plans/2026-06-10-attuned-1-4-resonant-depths.md` applies VERBATIM to this plan. Read it before starting.**
>
> **Collision rule:** this track deliberately touches ONLY files the 1.4 feature plan does not (tooling, CI, docs, test-debrittling, dead data). Before starting any task, run `git status --porcelain` — if a file you are about to edit is already modified by the other in-flight worker, SKIP that task and move on; come back after their work commits.

**Goal:** Pay down the audit-verified maintenance backlog: brittle test pins, dead lang keys, release engineering, tooling hardening, and data-cost consistency — all independently shippable, no gameplay features.

**Architecture / Tech Stack:** Same as the 1.4 plan.

---

### Task M1: Debrittle the worst test pins

The June 2026 audit identified the highest-friction source-grep assertions. Replace them with semantically equivalent, refactor-tolerant checks. The assertion INTENT must survive every change.

**Files:**
- Modify: `src/test/java/dev/attuned/client/UiAssetContractTest.java`
- Modify: `src/test/java/dev/attuned/client/FocusPresentationConsistencyTest.java`
- Modify: `src/test/java/dev/attuned/attunement/AttunedAttachmentsContractTest.java`

- [ ] **Step 1:** Read all three tests fully. In `UiAssetContractTest` (~lines 93–101): the Bind button's coordinates are pinned in FOUR places (Java constants, Python generator call-site text, fixtures JSON, and the test itself). Rewrite so the test reads `tools/asset_customizer/gui-fixtures.json` as the single source of truth and asserts the Java constants and the generated PNG geometry against IT — delete the Python call-site text pin entirely (the generated PNG's pixels already prove the generator ran with the right values).
- [ ] **Step 2:** In `FocusPresentationConsistencyTest` (~lines 32–41): five full player-facing sentences are pinned with `assertEquals`. Replace each with the per-key substring that carries the invariant (e.g. `"Focus Ability key"` and the numeric value like `"8 blocks"`), keeping the existing stale-phrase regex scan unchanged.
- [ ] **Step 3:** In `AttunedAttachmentsContractTest` (~lines 21–27): exact-statement pins (`"return clampCapacity(player.getAttachedOrElse(CAPACITY, 0));"`) become two-part pins: the public signature + the load-bearing fragment (`"clampCapacity("`, `"capacityCap()"`), so renaming a local or reformatting no longer fails the suite.
- [ ] **Step 4:** Full suite green:
  ```powershell
  .\gradlew.bat cleanTest test --no-daemon
  ```
- [ ] **Step 5:** Changelog `### Internal` bullet (under the current unreleased heading).

### Task M2: Delete dead lang keys + dormant journal copy drift

**Files:**
- Create: `src/test/java/dev/attuned/client/DeadLangKeyContractTest.java`
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json`

- [ ] **Step 1:** Write the failing test FIRST: it loads `en_us.json`, and for every `screen.attuned.*` key asserts the literal key string appears somewhere under `src/main/java` or `src/client/java` (walk the tree once, concatenate sources). Exempt nothing. Run it — it must fail listing exactly the dead keys (audit found 15: `screen.attuned.altar.attunement`, `.altar.stance`, four `.altar.forecast.*`, six `.altar.memory.*`, `.reweaving_altar.hint.title`, `.satchel.empty`, `.satchel.full` — VERIFY the live list from the test output, do not trust this enumeration).
- [ ] **Step 2:** Delete the dead keys the test reports. Re-run to green. CAUTION: `screen.attuned.satchel.empty`/`full` may have been re-referenced by 1.4 work — the test output is the truth.
- [ ] **Step 3:** Grep the journal page text (`journal.attuned.page*` values in lang) for mentions of features the keys belonged to (the audit flagged "Altar Memory" on page14/page22). If the feature text describes UI that no longer exists, rewrite those page strings minimally to match the current altar screen (read `AltarScreen.java` first). Update `FocusPresentationConsistencyTest`/journal pins if they reference the old copy.
- [ ] **Step 4:** Full suite + `python tools/verify_repository.py` green. Changelog bullet.

### Task M3: Tag-triggered release workflow

CI currently gates nothing about releases; a stale or dirty-tree jar can ship. Add a `release.yml` that runs on tag push.

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `docs/releasing.md`

- [ ] **Step 1:** Read `.github/workflows/ci.yml` (copy its SHA-pinned action versions, Java/Gradle setup, LFS checkout, env). Write `release.yml`: trigger `on: push: tags: ["v*"]`; jobs: (1) the full CI gate (repository checks, python tests, gradle `test build`, runtime smoke), (2) on success, `python tools/publish_curseforge.py --dry-run` and a Modrinth changelog-section presence check (run the same `currentChangelogSection` logic — simplest: `python tools/verify_repository.py` already validates the changelog section; cite that), (3) upload the built jar as a workflow artifact named `attuned-<version>.jar`. Do NOT auto-publish to Modrinth/CurseForge from CI — publishing stays a manual, user-authorized step.
- [ ] **Step 2:** Update `docs/releasing.md`: the release flow becomes "bump version + changelog → push → tag `v<version>` → CI green on the tag → download/verify artifact → run the two publish commands locally".
- [ ] **Step 3:** Validate the YAML: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/release.yml'))"` (if PyYAML is unavailable, use careful manual review + push to a throwaway tag like `v0.0.0-test` and delete it after: `git tag v0.0.0-test && git push origin v0.0.0-test`, watch the run, then `git push --delete origin v0.0.0-test; git tag -d v0.0.0-test`).
- [ ] **Step 4:** NOTE: pushing workflow files requires a credential with the `workflow` scope. If `git push` is rejected, push with the gh CLI token inline: `t=$(gh auth token); git push "https://x-access-token:${t}@github.com/defnotean/attuned.git" main`.

### Task M4: Publisher hardening

**Files:**
- Modify: `tools/publish_curseforge.py`
- Modify: `tests/test_publish_curseforge_contract.py`

- [ ] **Step 1 (test-first):** extend the contract test: (a) `build_metadata` gets `java_version` from a parameter that the CLI derives from `gradle.properties` (no hardcoded "25" — assert reading both `minecraft_version` and a new `java_version` derivation), (b) a new `verify_jar_freshness(jar_path, head_commit_time)` helper raises when the jar mtime predates the current `git log -1 --format=%ct` (stale-jar guard), (c) a `--release-type beta|release` flag flows into metadata.
- [ ] **Step 2:** Red → implement → green (`python -m unittest discover -s tests`).
- [ ] **Step 3:** Investigate the `gameVersionNames` field: check the CurseForge file page for the already-published 1.3.0 file — if its game-version tags are missing/wrong, the upload API ignored the field; switch to the documented `gameVersions` (numeric IDs) form, resolving IDs via the CF version-types endpoint at runtime with the token. If tags look right, leave the field and document why in a comment.

### Task M5: Data cost-consistency pass

**Files:**
- Modify: `src/main/resources/data/attuned/attuned/focus/drift_focus.json` (cost 2 → 3, add `"unique": true` — it cancels ALL fall damage; Emberward's full-immunity peer is 3+unique)
- Modify: `src/main/resources/data/attuned/attuned/focus/harborlight_focus.json` (cost 2 → 3 to match Driftglass's identical kit shape)
- Modify: `src/test/java/dev/attuned/content/FocusUniquenessContractTest.java` (add `drift_focus` to the set)
- Modify: `docs/reference.md` + lang if any tooltip states a cost

- [ ] **Step 1:** Before changing, grep `src/test` for `drift_focus` and `harborlight_focus` cost pins (`FocusDataConsistencyTest` scans costs — read its rules first; it may assert cost ranges rather than exact values).
- [ ] **Step 2:** Apply, full suite green, changelog under `### Changed` ("rebalanced Drift and Harborlight costs").
- [ ] **Step 3:** This is a BALANCE change — flag it prominently in the changelog so the release notes call it out.

### Task M6: Full Verification

- [ ] Run the complete gate and confirm everything green:
  ```powershell
  .\gradlew.bat cleanTest build --no-daemon
  python tools/verify_repository.py
  python -m unittest discover -s tests
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  git diff --check
  ```
