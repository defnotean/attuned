# Minecraft Version Migration Guide

This guide is the repeatable workflow for moving Attuned to a newer or older Minecraft version. It is intentionally conservative: the tooling can automate the current Fabric version metadata and checklist work, but Minecraft API changes, Fabric API changes, Quilt compatibility/native work, and Forge-family loader ports still require compile/test/QA passes.

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
- Quilt Loader/QFAPI availability and Quilt-native metadata differences.
- Forge/NeoForge build plugin, metadata, registry, state, networking, menu, event, and client hook differences.
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

These profiles describe the Fabric build tuple only. Quilt, Forge, and NeoForge
support uses `config/loader-support-profiles.json` and the loader-specific
checklists once a non-Fabric branch exists. Do not treat `loader_version` as a
generic loader field; in this registry it means Fabric Loader.

Recommended `status` values:

- `current` — currently released/maintained target.
- `candidate` — planned target that still needs compile/QA.
- `maintenance` — older target supported by a maintenance branch.
- `blocked` — profile is documented, but dependencies or mappings are not ready.
- `dropped` — intentionally no longer supported.

## Loader Tracks

Minecraft version ports and loader ports are separate axes:

| Track | Current status | Main metadata | Main verification |
| --- | --- | --- | --- |
| Fabric | Implemented and published | `fabric.mod.json`, Fabric Loader/API/Loom fields, Modrinth `fabric`, CurseForge `Fabric` | Gradle build, Fabric `runServer`, Fabric `runClient` when needed |
| Quilt compatibility | Documented validation track, not currently verified | Fabric metadata plus Quilt Loader/QFAPI evidence, platform copy that says compatible rather than native | Quilt server smoke, Quilt client smoke, dependency availability check |
| Quilt native | 1.19.2 and 1.20.6 branch build/server-smoke candidates; newer targets planned or blocked by dependency strategy | `quilt.mod.json`, Quilt Loom/Loader fields, target-specific API dependency strategy, platform `Quilt` tag | separate Quilt build, server smoke, client smoke, hands-on HUD smoke, metadata dry run |
| NeoForge | 1.20.6, 1.21.1, 1.21.11, 26.1.2, and 26.2 branch build/server-smoke candidates; 1.20.1 blocked behind legacy coordinate work | `neoforge.mods.toml`, ModDevGradle fields, platform `NeoForge` tag | separate NeoForge build, server smoke, client smoke, hands-on HUD smoke, metadata dry run |
| Forge | Branch build candidates across the audited Forge targets | `mods.toml`, ForgeGradle fields, platform `Forge` tag | separate Forge build, server smoke, client smoke, hands-on HUD smoke, metadata dry run |

Use `docs/loader-support.md` and
`docs/superpowers/plans/2026-06-25-loader-port-roadmap.md` before starting
loader work. A Quilt, Forge, or NeoForge port
must not be represented as a Fabric profile change; it needs its own adapter
for entrypoints, events, registries, player state, networking, menu/screen
registration, config paths, mixins/access, and publishing metadata.

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

List loader support profiles:

```bash
python tools/loader_support_profile.py list
```

Validate loader support profiles:

```bash
python tools/loader_support_profile.py validate
```

Generate a loader checklist:

```bash
python tools/loader_support_profile.py render-checklist neoforge-26.1.2 --output docs/versioning/checklists/loader-neoforge-minecraft-26.1.2.md
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
   python -m pip install -r requirements-dev.txt
   python tools/verify_repository.py
   python -m unittest discover -s tests
   python -m pytest tests/ -q
   ./gradlew build --no-daemon
   python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
   ```

9. Use `./gradlew runClient` only when UI/client behavior changed or the server smoke cannot cover the risk.

10. Update README/changelog/platform metadata only after the port is green.

## Non-Fabric Loader Port Workflow

Use this when the Minecraft version is known and the goal is Quilt compatibility,
a Quilt-native artifact, a Forge artifact, or a NeoForge artifact for that
version.

1. Start from a green branch whose gameplay/content state should be shared.

   ```bash
   git checkout latest
   git pull --ff-only origin latest
   git checkout -b port/<loader>-<minecraft-version>
   ```

2. Decide the exact loader family and version: Quilt compatibility, Quilt
   native, Forge, or NeoForge; Minecraft version; Java target; build Java;
   Gradle plugin; mappings; and dependency ranges.

3. Add loader metadata and build wiring without changing gameplay content:
   future `quilt.mod.json`, `META-INF/mods.toml`, or
   `META-INF/neoforge.mods.toml`; Quilt Loom, ForgeGradle, or NeoGradle
   configuration; loader-specific source sets/modules; and a jar name that
   cannot be confused with the Fabric artifact.

4. Port the loader boundaries named in `docs/loader-support.md`: entrypoint,
   registries, commands, events, state persistence/sync, networking, menus,
   client setup, config paths, mixins/access, and optional dependency metadata.

5. Compile before editing gameplay logic. Fix loader API and mapping errors in
   small batches, then add or adjust tests for behavior whose event timing or
   cancellation semantics differs from Fabric.

6. Run full loader verification: repository verifier, Python contracts,
   loader-specific Gradle tests/build, dedicated server smoke, client smoke, UI
   inspection, state persistence checks, payload checks, and platform metadata
   dry run.

7. Update README/changelog/platform metadata only after the loader work is
   green. The release copy must clearly name Quilt compatibility, Quilt native,
   Forge, or NeoForge and must not imply that the Fabric jar works on a loader
   where it has not been smoke-tested.

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

For Quilt, Forge, or NeoForge builds, extend the gate before publishing so repository
verification can also prove loader metadata, platform tags, dependency
declarations, and build outputs match the loader target.

## Release Metadata Rules

- Modrinth game versions come from `project.minecraft_version` in `build.gradle` for the current Fabric build.
- CurseForge game version names come from `minecraft_version` and `java_version` in `gradle.properties` for the current Fabric build.
- Quilt, Forge, and NeoForge releases need separate platform metadata that names the actual loader and does not reuse the Fabric dependency list.
- Platform descriptions and release notes should be changed only after compile/build/smoke pass.
- For a completed, green Attuned change, merge to `latest`, push `latest`, and watch GitHub CI to success.

## Rollback Rules

- If a profile application causes dependency resolution failure, revert the profile application commit first.
- If compile fails due to mappings/API drift, keep the branch and fix in small tested batches.
- If the target is blocked by missing Fabric API/Loom support, do not publish or merge; mark the profile `blocked` with exact notes.
- If a Quilt track is blocked by missing Quilt Loader/QFAPI support, keep it documented as blocked and do not call the Fabric artifact Quilt-compatible.
- If a Forge-family port is blocked by loader API, mapping, metadata, or runtime gaps, keep it off public platform pages until a separate Forge/NeoForge artifact passes its own verification.
