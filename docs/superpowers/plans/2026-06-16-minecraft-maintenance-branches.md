# Minecraft Maintenance Branches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create and verify independent Attuned branches for the latest Minecraft release line and the four prior major Minecraft release families.

**Architecture:** Keep `latest` as the newest development branch. Each older target has its own `maintenance/minecraft-<version>` branch with a matching active profile in `config/minecraft-version-profiles.json`, branch-local Gradle/Fabric metadata, and independent verification results.

**Tech Stack:** Fabric Loom, Gradle, Fabric Loader/API, Python repository verification, GitHub Actions.

---

### Task 1: Default Branch Rename

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/versioning/minecraft-version-migration.md`
- Modify: `tools/minecraft_version_profile.py`
- Test: `tests/test_docs_contract.py`

- [ ] **Step 1: Write the failing branch-name contract**

```python
def test_default_development_branch_is_latest(self) -> None:
    ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
    migration = (ROOT / "docs" / "versioning" / "minecraft-version-migration.md").read_text(encoding="utf-8")

    self.assertIn("branches: [latest]", ci)
    self.assertNotIn("branches: [main]", ci)
    self.assertIn("tools/minecraft_version_profile.py current --github-output", ci)
    self.assertIn("steps.versions.outputs.java_version", ci)
    self.assertIn("Start from green `latest`", migration)
    self.assertNotIn("git checkout main", migration)
```

- [ ] **Step 2: Verify red**

Run: `python -B -m unittest tests.test_docs_contract.DocsContractTest.test_default_development_branch_is_latest`

Expected: FAIL because the workflow and migration docs still reference `main` or hardcoded Java.

- [ ] **Step 3: Update CI and docs**

Use `latest` in workflow triggers, add a `Resolve Minecraft version profile` step with id `versions`, and wire `setup-java` to `${{ steps.versions.outputs.java_version }}`. Update migration docs and checklist generation from `main` to `latest`.

- [ ] **Step 4: Verify green**

Run: `python -B -m unittest tests.test_docs_contract.DocsContractTest.test_default_development_branch_is_latest`

Expected: PASS.

### Task 2: Version Matrix

**Files:**
- Modify: `config/minecraft-version-profiles.json`
- Create: `docs/versioning/supported-branches.md`
- Test: `tests/test_minecraft_version_profile_contract.py`
- Test: `tests/test_docs_contract.py`

- [ ] **Step 1: Write the failing profile-matrix contract**

```python
def test_repository_profiles_cover_latest_and_four_maintenance_targets(self) -> None:
    profiles = minecraft_version_profile.load_profiles(ROOT)["profiles"]

    self.assertEqual("26.1.2", minecraft_version_profile.load_profiles(ROOT)["active_profile"])
    expected = {
        "26.1.2": "current",
        "1.21.11": "maintenance",
        "1.20.6": "maintenance",
        "1.19.4": "maintenance",
        "1.18.2": "maintenance",
    }
    for profile_id, status in expected.items():
        self.assertIn(profile_id, profiles)
        self.assertEqual(status, profiles[profile_id]["status"])
```

- [ ] **Step 2: Verify red**

Run: `python -B -m unittest tests.test_minecraft_version_profile_contract.MinecraftVersionProfileContractTest.test_repository_profiles_cover_latest_and_four_maintenance_targets`

Expected: FAIL because the older maintenance profiles are not registered yet.

- [ ] **Step 3: Add profiles and matrix docs**

Add profiles for `1.21.11`, `1.20.6`, `1.19.4`, and `1.18.2`. Add `docs/versioning/supported-branches.md` with the branch matrix and dependency tuple for each target.

- [ ] **Step 4: Verify green**

Run: `python -B -m unittest tests.test_minecraft_version_profile_contract.MinecraftVersionProfileContractTest.test_repository_profiles_cover_latest_and_four_maintenance_targets tests.test_docs_contract.DocsContractTest.test_supported_branch_docs_cover_version_matrix`

Expected: PASS.

### Task 3: Branch Creation

**Files:**
- Branch: `latest`
- Branch: `maintenance/minecraft-1.21.11`
- Branch: `maintenance/minecraft-1.20.6`
- Branch: `maintenance/minecraft-1.19.4`
- Branch: `maintenance/minecraft-1.18.2`

- [ ] **Step 1: Push `latest` and set default branch**

Run:

```powershell
git branch -m latest
git push -u origin latest
gh repo edit --default-branch latest
git push origin --delete main
```

Expected: GitHub default branch is `latest`; remote `main` is gone.

- [ ] **Step 2: Remove stale merged branch**

Run:

```powershell
git push origin --delete defnotean/focus-art-replacements
```

Expected: the merged temporary branch is removed from the remote.

### Task 4: Maintenance Branch Profiles

**Files:**
- Modify per branch: `config/minecraft-version-profiles.json`
- Modify per branch: `gradle.properties`
- Modify per branch: `src/main/resources/fabric.mod.json`
- Modify per branch: `README.md`
- Modify per branch: `.github/workflows/ci.yml`

- [ ] **Step 1: Create and apply each branch profile**

For each target:

```powershell
git checkout -B maintenance/minecraft-<version> latest
python tools\minecraft_version_profile.py apply <version>
```

Then set `active_profile` to `<version>` in `config/minecraft-version-profiles.json`, update `fabric.mod.json` Minecraft range to the exact target family, and update branch-local README requirements.

- [ ] **Step 2: Commit each branch independently**

Run:

```powershell
git add config/minecraft-version-profiles.json gradle.properties src/main/resources/fabric.mod.json README.md .github/workflows/ci.yml
git commit -m "Port metadata to Minecraft <version>"
```

Expected: one clean metadata commit per maintenance branch.

### Task 5: Verification

**Files:**
- Branch-local code and resources as needed.

- [ ] **Step 1: Run branch-local repository checks**

Run on each branch:

```powershell
python -B tools\verify_repository.py
python -B -m unittest discover -s tests
git diff --check
.\gradlew.bat test build --no-daemon
python -B tools\minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
```

Expected: all commands pass. If compile fails from mapping/API drift, add a failing Java/Python contract for the behavior being fixed, implement the compatibility change, and rerun the full branch gate.

- [ ] **Step 2: Document results**

Update `docs/versioning/supported-branches.md` on `latest` with the actual verification status for each branch after all branches are green or explicitly blocked with exact reasons.
