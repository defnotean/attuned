# Famous Minecraft Version Ports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add and verify Attuned maintenance ports for the highest-value missing Minecraft versions while keeping Forge-era classics clearly separated from Fabric maintenance support.

**Architecture:** Keep `latest` as the newest Fabric line and create each new port from the nearest already-verified maintenance branch. Candidate profiles live in `config/minecraft-version-profiles.json` until their branches pass local and CI gates. Forge-era versions are documented as prototype/rewrite work, not maintenance branches.

**Tech Stack:** Fabric Loom, Gradle, Fabric Loader/API, Python repository verification, Minecraft runtime smoke tests, GitHub Actions, Modrinth/CurseForge metadata tools.

---

## Target List

| Priority | Minecraft | Track | Base branch | Reason |
| --- | --- | --- | --- | --- |
| 1 | `1.20.1` | Fabric maintenance candidate | `maintenance/minecraft-1.20.6` | Highest missing Modrinth total-mod count and a common modpack target. |
| 2 | `1.21.1` | Fabric maintenance candidate | `maintenance/minecraft-1.21.11` | Strong modern modding target with large Fabric coverage. |
| 3 | `1.19.2` | Fabric maintenance candidate | `maintenance/minecraft-1.19.4` | Recognizable 1.19 modpack target; likely smaller delta than 1.16.5. |
| 4 | `1.16.5` | high-effort Fabric backport | none yet | Famous, but requires Java 8 bytecode/build-script support and older APIs. |
| 5 | `1.17.1` | high-effort Fabric backport | none yet | Smaller audience than 1.16.5 and still needs older API handling. |
| 6 | `1.12.2`, `1.8.9`, `1.7.10` | legacy rewrite | `prototype/forge-*` only | Forge/pre-Fabric architecture; not a branch-local Fabric profile. |

### Task 1: Prepare Shared Version Registry

**Files:**
- Modify: `.gitignore`
- Modify: `config/minecraft-version-profiles.json`
- Modify: `docs/versioning/supported-branches.md`

- [x] **Step 1: Ignore local branch worktrees**

Add `/.worktrees/` to `.gitignore` under local tooling scratch dirs.

- [x] **Step 2: Add candidate profiles**

Add candidate entries for:

```json
"1.21.1": {
  "minecraft_version": "1.21.1",
  "loader_version": "0.19.3",
  "loom_version": "1.17.11",
  "fabric_api_version": "0.116.12+1.21.1",
  "java_version": "21",
  "fabric_loader_range": ">=0.19.3",
  "status": "candidate"
}
```

```json
"1.20.1": {
  "minecraft_version": "1.20.1",
  "loader_version": "0.19.3",
  "loom_version": "1.17.11",
  "fabric_api_version": "0.92.9+1.20.1",
  "java_version": "17",
  "fabric_loader_range": ">=0.19.3",
  "status": "candidate"
}
```

```json
"1.19.2": {
  "minecraft_version": "1.19.2",
  "loader_version": "0.19.3",
  "loom_version": "1.17.11",
  "fabric_api_version": "0.77.0+1.19.2",
  "java_version": "17",
  "fabric_loader_range": ">=0.19.3",
  "status": "candidate"
}
```

- [ ] **Step 3: Verify shared registry**

Run:

```powershell
python -B -m unittest tests.test_minecraft_version_profile_contract tests.test_docs_contract
python -B tools\verify_repository.py
git diff --check
```

Expected: all commands exit `0`; repository validation still reports active profile `26.2`.

### Task 2: Port Minecraft 1.21.1

**Branch:** `maintenance/minecraft-1.21.1`

**Base:** `origin/maintenance/minecraft-1.21.11`

- [ ] **Step 1: Create branch**

```powershell
git switch -c maintenance/minecraft-1.21.1 origin/maintenance/minecraft-1.21.11
```

- [ ] **Step 2: Add branch-local profile and apply it**

Ensure `config/minecraft-version-profiles.json` has active profile `1.21.1`, then run:

```powershell
python tools\minecraft_version_profile.py apply 1.21.1
python tools\minecraft_version_profile.py validate
```

- [ ] **Step 3: Run branch gates**

```powershell
python -B tools\verify_repository.py
python -B -m unittest discover -s tests
.\gradlew.bat test build --no-daemon --console=plain
```

- [ ] **Step 4: Runtime smoke**

```powershell
python tools\minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
.\gradlew.bat runClient --no-daemon --console=plain
```

Record any compile/runtime blockers in `docs/versioning/supported-branches.md`.

### Task 3: Port Minecraft 1.20.1

**Branch:** `maintenance/minecraft-1.20.1`

**Base:** `origin/maintenance/minecraft-1.20.6`

- [ ] **Step 1: Create branch**

```powershell
git switch -c maintenance/minecraft-1.20.1 origin/maintenance/minecraft-1.20.6
```

- [ ] **Step 2: Add branch-local profile and apply it**

Ensure `config/minecraft-version-profiles.json` has active profile `1.20.1`, then run:

```powershell
python tools\minecraft_version_profile.py apply 1.20.1
python tools\minecraft_version_profile.py validate
```

- [ ] **Step 3: Run branch gates**

```powershell
python -B tools\verify_repository.py
python -B -m unittest discover -s tests
.\gradlew.bat test build --no-daemon --console=plain
```

Record compile blockers before making compatibility edits.

### Task 4: Port Minecraft 1.19.2

**Branch:** `maintenance/minecraft-1.19.2`

**Base:** `origin/maintenance/minecraft-1.19.4`

- [ ] **Step 1: Create branch**

```powershell
git switch -c maintenance/minecraft-1.19.2 origin/maintenance/minecraft-1.19.4
```

- [ ] **Step 2: Add branch-local profile and apply it**

Ensure `config/minecraft-version-profiles.json` has active profile `1.19.2`, then run:

```powershell
python tools\minecraft_version_profile.py apply 1.19.2
python tools\minecraft_version_profile.py validate
```

- [ ] **Step 3: Run branch gates**

```powershell
python -B tools\verify_repository.py
python -B -m unittest discover -s tests
.\gradlew.bat test build --no-daemon --console=plain
```

Record compile blockers before making compatibility edits.

### Task 5: Legacy Rewrite Track

**Branches:** use `prototype/forge-1.12.2`, `prototype/forge-1.8.9`, or `prototype/forge-1.7.10` only after a separate Forge plan exists.

- [ ] **Step 1: Do not add these to Fabric profile registry**

Keep `1.12.2`, `1.8.9`, and `1.7.10` out of `config/minecraft-version-profiles.json` because the current build, metadata, lifecycle, networking, persistence, resources, and tests are Fabric-specific.

- [ ] **Step 2: Plan Forge prototype separately**

Create a separate plan that defines ForgeGradle, mappings, Java 8, lifecycle, networking, capability/NBT persistence, GUI/HUD hooks, loot/recipe/worldgen replacement, and branch-specific QA before creating any legacy prototype branch.
