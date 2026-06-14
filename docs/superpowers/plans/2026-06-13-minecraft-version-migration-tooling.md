# Minecraft Version Migration Tooling Implementation Plan

> **For Hermes:** Implement this plan with strict TDD for tooling and verification changes. Keep the current 26.1.2 release behavior unchanged while adding a safe path for future newer/older Minecraft ports.

**Goal:** Make Attuned significantly easier to retarget to a newer or older Minecraft version by centralizing version metadata, adding a repeatable port-prep CLI, validating all version knobs, and documenting an exact branch/test/release workflow.

**Architecture:** Add a JSON version profile registry as the single human-edited source for Minecraft/Fabric/Loom/Java combinations. Add a stdlib Python CLI that validates profiles, reports the active profile, applies a profile to `gradle.properties`, and renders a port checklist. Wire Gradle, CI, repository verification, and release tooling to consume the same version properties instead of hardcoded literals.

**Tech Stack:** Python 3.11 stdlib, Gradle/Fabric Loom, GitHub Actions, existing `tools/verify_repository.py`, existing Python unittest suite, existing Gradle/JUnit test suite.

---

## Current State

- Current released target is Minecraft `26.1.2` with Fabric Loader `0.19.2`, Fabric API `0.149.0+26.1.2`, Loom `1.16.3`, Java `25`.
- `gradle.properties` owns `minecraft_version`, `loader_version`, `loom_version`, and `fabric_api_version`, but Java is hardcoded in `build.gradle` and GitHub Actions.
- `build.gradle` hardcodes Modrinth `gameVersions = ["26.1.2"]` and Java compile/toolchain `25`.
- `tools/publish_curseforge.py` accepts a `java_version` argument but `main()` hardcodes `"25"`.
- CI uses `java-version: "25"` directly.
- There is no checklist generator, no profile validation, and no automated warning when docs/tools drift from the active version target.

## Non-Goals / Reality Check

- This will not magically make a Minecraft API-breaking port compile. Older/newer Minecraft versions may require source changes, mapping changes, data format migrations, or dependency changes.
- The tooling should make the mechanical retargeting steps repeatable: change the version profile, update Gradle/CI/release metadata, run checks, and generate a precise checklist of likely manual porting tasks.
- Multiple simultaneous build outputs for many Minecraft versions are a future phase. This phase prepares one active target at a time safely.

## Deliverables

1. `config/minecraft-version-profiles.json` containing the active profile and known profiles.
2. `tools/minecraft_version_profile.py` CLI with:
   - `list`
   - `current`
   - `validate`
   - `apply <profile> [--dry-run]`
   - `render-checklist <profile> --output <path>`
3. `gradle.properties` gains `java_version=25`.
4. `build.gradle` consumes `java_version` and `minecraft_version` dynamically.
5. `.github/workflows/ci.yml` remains protected by GitHub workflow-scope credentials; repository verification now checks its `java-version` against the active profile and can also accept a future dynamic profile-read step.
6. `tools/publish_curseforge.py` reads `java_version` from `gradle.properties`.
7. `tools/verify_repository.py` validates the version profile registry against `gradle.properties`, `build.gradle`, CI, docs, and release tooling.
8. Tests covering profile validation, dry-run/apply behavior, checklist generation, and verifier drift reporting.
9. `docs/versioning/minecraft-version-migration.md` as the human workflow for future newer/older ports.
10. A generated current-target checklist at `docs/versioning/checklists/minecraft-26.1.2.md` proving the workflow works.

---

## Implementation Tasks

### Task 1: Add Version Profile Contract Tests

**Objective:** Pin the expected profile schema, active profile, and CLI behavior before implementing the tool.

**Files:**
- Create: `tests/test_minecraft_version_profile_contract.py`
- Create later: `tools/minecraft_version_profile.py`
- Create later: `config/minecraft-version-profiles.json`

**Tests:**
- Profile file exists and contains `active_profile`.
- Active profile is `26.1.2` and contains `minecraft_version`, `loader_version`, `loom_version`, `fabric_api_version`, `java_version`, `fabric_loader_range`, `status`, `notes`.
- `validate_profiles()` returns no problems for the repo profile.
- `apply_profile(..., dry_run=True)` reports intended `gradle.properties` changes without writing.
- Applying the current profile to a temp repo updates only version keys and preserves unrelated Gradle properties.
- Checklist renderer includes branch naming, dependency bump, data/resource checks, Java checks, Gradle build, dedicated server smoke, client smoke, Modrinth/CurseForge metadata, and rollback notes.

**Verification:**

```bash
python -m unittest tests.test_minecraft_version_profile_contract -v
```

Expected RED before implementation: missing `tools/minecraft_version_profile.py` or missing profile file.

### Task 2: Implement Profile Config and CLI

**Objective:** Provide one maintained profile registry and a safe command-line entrypoint.

**Files:**
- Create: `config/minecraft-version-profiles.json`
- Create: `tools/minecraft_version_profile.py`

**Profile Schema:**

```json
{
  "active_profile": "26.1.2",
  "profiles": {
    "26.1.2": {
      "minecraft_version": "26.1.2",
      "loader_version": "0.19.2",
      "loom_version": "1.16.3",
      "fabric_api_version": "0.149.0+26.1.2",
      "java_version": "25",
      "fabric_loader_range": ">=0.19.2",
      "status": "current",
      "notes": ["Current released target."]
    }
  }
}
```

**CLI Commands:**

```bash
python tools/minecraft_version_profile.py list
python tools/minecraft_version_profile.py current
python tools/minecraft_version_profile.py validate
python tools/minecraft_version_profile.py apply 26.1.2 --dry-run
python tools/minecraft_version_profile.py render-checklist 26.1.2 --output docs/versioning/checklists/minecraft-26.1.2.md
```

**Implementation Details:**
- Use only Python stdlib.
- Never rewrite the whole `gradle.properties` file; preserve comments and ordering.
- Add missing version keys immediately after the nearest version section when possible.
- `apply --dry-run` prints JSON with `changed`, `profile`, and `updates`.
- `apply` writes only `minecraft_version`, `loader_version`, `loom_version`, `fabric_api_version`, `java_version`.
- `validate` checks required fields, active profile existence, semver-ish Java integer, Fabric API suffix includes Minecraft version, and active Gradle properties match the active profile.

### Task 3: Remove Hardcoded Version Knobs

**Objective:** Make Gradle, CI, and release metadata follow the profile-applied `gradle.properties` values.

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `tools/publish_curseforge.py`
- Test: `tests/test_publish_curseforge_contract.py`
- Test: `tests/test_verify_repository_contract.py`

**Changes:**
- Add `java_version=25` to `gradle.properties`.
- In `build.gradle`, replace all Java `25` literals in compile/toolchain settings with `project.java_version.toInteger()`.
- In `build.gradle`, replace Modrinth `gameVersions = ["26.1.2"]` with `gameVersions = [project.minecraft_version.toString()]`.
- In CI, leave `.github/workflows/ci.yml` unchanged in this commit because the available GitHub credential cannot push workflow-file edits; instead, make `tools/verify_repository.py` fail if CI's `java-version` stops matching the active profile. If a workflow-scoped token/SSH credential is available later, this optional dynamic CI step can replace the hardcoded value:

```yaml
- name: Read Minecraft version profile
  id: versions
  run: python3 tools/minecraft_version_profile.py current --github-output "$GITHUB_OUTPUT"
```

Then set Java with:

```yaml
java-version: ${{ steps.versions.outputs.java_version }}
```

- In `tools/publish_curseforge.py`, read `java_version = props["java_version"]` instead of hardcoding `"25"`.

### Task 4: Wire Repository Verification

**Objective:** Fail fast when version profile, Gradle, CI, or release tooling drift apart.

**Files:**
- Modify: `tools/verify_repository.py`
- Modify: `tests/test_verify_repository_contract.py`

**New Verifier Checks:**
- Profile file parses as JSON.
- Active profile exists.
- Active profile version keys match `gradle.properties`.
- `build.gradle` uses `project.java_version` and `project.minecraft_version` for Java/Modrinth fields.
- CI `java-version` matches the active profile or uses `minecraft_version_profile.py current --github-output` dynamically.
- `tools/publish_curseforge.py` reads `java_version` from properties.
- `docs/versioning/minecraft-version-migration.md` exists.

### Task 5: Add Human Porting Documentation

**Objective:** Give future you a step-by-step migration path that works for newer and older Minecraft versions.

**Files:**
- Create: `docs/versioning/minecraft-version-migration.md`
- Create: `docs/versioning/checklists/minecraft-26.1.2.md`

**Docs Must Cover:**
- Create branch `port/minecraft-<version>`.
- Add a new profile entry with exact dependency versions from Fabric's develop page.
- Run `validate`, `apply --dry-run`, and `apply`.
- Run Gradle dependency resolution and update locks if needed.
- Run source-level searches for API/mapping hotspots.
- Run `python tools/verify_repository.py`, `python -m unittest discover -s tests`, `./gradlew test`, `./gradlew build`, and server smoke.
- Launch client only when required.
- Update release docs/platform metadata only after build/smoke pass.
- Keep older-version support on a maintenance branch when source compatibility diverges.
- Backport policy: bugfix-only unless the code path is shared and fully tested.

### Task 6: Full Verification and Merge

**Objective:** Prove the migration tooling is safe, then land it on `main`.

**Commands:**

```bash
python tools/minecraft_version_profile.py validate
python tools/minecraft_version_profile.py apply 26.1.2 --dry-run
python tools/minecraft_version_profile.py render-checklist 26.1.2 --output docs/versioning/checklists/minecraft-26.1.2.md
python tools/verify_repository.py
python -m unittest discover -s tests
uv run --with pytest --with pillow -m pytest tests/ -q
./gradlew build
```

**Git:**

```bash
git add -A
git commit -m "tooling: add Minecraft version migration profiles"
git checkout main
git merge --no-ff tooling/minecraft-version-migration
./gradlew build
python tools/verify_repository.py
python -m unittest discover -s tests
git push origin main
gh run watch <new-main-run> --exit-status
```

---

## Future Phase Ideas

- Add `port-check <from> <to>` that compares two profiles and lists required dependency-lock changes.
- Add per-Minecraft-version source sets only if a real older/newer port proves they are necessary.
- Add a CI matrix for active + next candidate once dependency downloads are stable enough.
- Add automatic Modrinth/CurseForge game-version metadata patching after a new release is uploaded.
- Add `docs/versioning/compatibility-matrix.md` generated from the profile registry.

## Acceptance Criteria

- A future port starts by adding one profile entry, not hunting for version literals across the repo.
- `apply --dry-run` shows exactly what will change before mutation.
- CI Java version follows the repository version profile.
- Release metadata follows `gradle.properties` for Minecraft and Java versions.
- Repository verification fails when active profile metadata drifts from Gradle/CI/release tooling.
- All existing tests/build checks still pass for the current `26.1.2` release.
