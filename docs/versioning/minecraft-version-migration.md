# Minecraft Version Migration Guide

This guide is the repeatable workflow for moving Attuned to a newer or older Minecraft version. It is intentionally conservative: the tooling can automate the version metadata and checklist work, but Minecraft/Fabric API changes still require compile/test/QA passes.

## What the Tooling Automates

- Keeps the active Minecraft/Fabric/Loom/Java tuple in `config/minecraft-version-profiles.json`.
- Applies a selected profile to `gradle.properties` without rewriting unrelated properties or comments.
- Makes Gradle compile/toolchain settings follow `java_version`.
- Makes Modrinth upload metadata follow `minecraft_version`.
- Makes CurseForge upload metadata follow `minecraft_version` and `java_version`.
- Keeps GitHub Actions' Java version pinned to the active profile through repository verification; switching CI to read the profile dynamically is a follow-up once a workflow-scoped GitHub token/SSH credential is available.
- Generates a port checklist for a target version.
- Fails repository verification when profile, Gradle, CI, or release metadata drift apart.

## What Still Requires Human Porting

- Minecraft mapping/package/signature changes.
- Fabric API behavior changes or unavailable target builds.
- Datapack/resource/schema changes.
- Structure/template DataVersion compatibility.
- Client rendering/menu/HUD changes caused by Minecraft internals.
- Release-note/platform copy after a port is actually verified.

## Version Profile Registry

The registry lives at:

```text
config/minecraft-version-profiles.json
```

Each profile key must equal its `minecraft_version`. Required fields:

```json
{
  "minecraft_version": "26.2",
  "loader_version": "0.19.3",
  "loom_version": "1.17.11",
  "fabric_api_version": "0.152.1+26.2",
  "java_version": "25",
  "fabric_loader_range": ">=0.19.3",
  "status": "current",
  "notes": ["Current released target."]
}
```

Recommended `status` values:

- `current` — currently released/maintained target.
- `candidate` — planned target that still needs compile/QA.
- `maintenance` — older target supported by a maintenance branch.
- `blocked` — profile is documented, but dependencies or mappings are not ready.
- `dropped` — intentionally no longer supported.

## Daily Commands

List profiles:

```bash
python tools/minecraft_version_profile.py list
```

Show the active profile:

```bash
python tools/minecraft_version_profile.py current
```

Validate registry + active Gradle alignment:

```bash
python tools/minecraft_version_profile.py validate
```

Preview profile application:

```bash
python tools/minecraft_version_profile.py apply <minecraft-version> --dry-run
```

Apply a profile:

```bash
python tools/minecraft_version_profile.py apply <minecraft-version>
```

Generate a checklist:

```bash
python tools/minecraft_version_profile.py render-checklist <minecraft-version> --output docs/versioning/checklists/minecraft-<minecraft-version>.md
```

## Newer Minecraft Version Workflow

1. Start from green `latest`.

   ```bash
   git checkout latest
   git pull --ff-only origin latest
   git checkout -b port/minecraft-<new-version>
   ```

2. Check Fabric's develop page for the exact tuple:
   - Minecraft version
   - Fabric Loader version
   - Fabric API version
   - Loom version
   - required Java version

3. Add a new profile to `config/minecraft-version-profiles.json` with `status: "candidate"`.

4. Generate the checklist.

   ```bash
   python tools/minecraft_version_profile.py render-checklist <new-version> --output docs/versioning/checklists/minecraft-<new-version>.md
   ```

5. Dry-run and apply the profile.

   ```bash
   python tools/minecraft_version_profile.py apply <new-version> --dry-run
   python tools/minecraft_version_profile.py apply <new-version>
   python tools/minecraft_version_profile.py validate
   ```

6. Compile before editing gameplay code.

   ```bash
   ./gradlew test --no-daemon
   ```

7. Fix mapping/API failures in small batches. Prefer local compatibility helpers only after at least two real target versions need divergent code.

8. Run full verification.

   ```bash
   python tools/verify_repository.py
   python -m unittest discover -s tests
   uv run --with pytest --with pillow -m pytest tests/ -q
   ./gradlew build --no-daemon
   python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
   ```

9. Use `./gradlew runClient` only when UI/client behavior changed or the server smoke cannot cover the risk.

10. Update README/changelog/platform metadata only after the port is green.

## Older Minecraft Version Workflow

Older support should normally live on a maintenance branch unless the codebase remains source-compatible.

1. Branch from the closest release tag or from current `latest` if the target is still source-compatible.

   ```bash
   git checkout latest
   git checkout -b maintenance/minecraft-<old-version>
   ```

2. Add the older profile with `status: "maintenance"`.

3. Apply the profile and compile. Expect more mapping and API drift than a newer point release.

4. Keep the maintenance branch bugfix-first. Do not backport new content unless:
   - it has tests,
   - it does not destabilize the older target,
   - and the release notes clearly identify the supported Minecraft version.

5. If dependencies are unavailable, mark the profile `blocked` with notes instead of force-pinning incompatible jars.

## Repository Gate

`python tools/verify_repository.py` now validates that:

- active profile metadata matches `gradle.properties`,
- `build.gradle` uses `java_version` and `minecraft_version` dynamically,
- CI `java-version` matches the active profile or uses the profile dynamically,
- CurseForge publishing reads Java from `gradle.properties`,
- this guide exists.

## Release Metadata Rules

- Modrinth game versions come from `project.minecraft_version` in `build.gradle`.
- CurseForge game version names come from `minecraft_version` and `java_version` in `gradle.properties`.
- Platform descriptions and release notes should be changed only after compile/build/smoke pass.
- For a completed, green Attuned change, merge to `latest`, push `latest`, and watch GitHub CI to success.

## Rollback Rules

- If a profile application causes dependency resolution failure, revert the profile application commit first.
- If compile fails due to mappings/API drift, keep the branch and fix in small tested batches.
- If the target is blocked by missing Fabric API/Loom support, do not publish or merge; mark the profile `blocked` with exact notes.
