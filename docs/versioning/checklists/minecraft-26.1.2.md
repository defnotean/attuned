# Attuned Minecraft 26.1.2 Port Checklist

Target profile: `26.1.2`
Branch: `maintenance/minecraft-26.1.2`

## Version Profile

- Minecraft: `26.1.2`
- Java: `25`
- Fabric Loader: `0.19.3`
- Fabric API: `0.152.1+26.1.2`
- Fabric Loom: `1.17.11`
- Loader range for metadata/docs: `>=0.19.3`
- Status: `maintenance`

## 1. Branch and Mechanical Retarget

- [ ] Start from green `latest`: `git checkout latest && git pull --ff-only origin latest`.
- [ ] Create the port branch: `git checkout -b maintenance/minecraft-26.1.2`.
- [ ] Confirm or add the profile in `config/minecraft-version-profiles.json`.
- [ ] Dry run profile application: `python tools/minecraft_version_profile.py apply 26.1.2 --dry-run`.
- [ ] Apply the profile: `python tools/minecraft_version_profile.py apply 26.1.2`.
- [ ] Run `python tools/minecraft_version_profile.py validate`.

## 2. Dependency and Lockfile Pass

- [ ] Check Fabric's develop page for the exact Minecraft, Fabric Loader, Fabric API, and Loom combination.
- [ ] Refresh dependency locks only after the profile is applied: `./gradlew dependencies --write-locks` if Gradle reports lock drift.
- [ ] Review dependency locks for accidental unrelated upgrades.
- [ ] If Fabric API is unavailable for this target, stop and mark the profile `blocked` instead of forcing a bad build.

## 3. Source and Mapping Compatibility Pass

- [ ] Search for direct Minecraft API hotspots: `git grep -n -E "net.minecraft|BuiltInRegistries|DataComponents|ServerPlayer|ResourceKey" src`.
- [ ] Compile first: `./gradlew test --no-daemon`.
- [ ] Fix mapping/package/signature errors one group at a time.
- [ ] For older Minecraft ports, prefer a maintenance branch if source compatibility diverges heavily.
- [ ] For newer Minecraft ports, keep compatibility shims small and tested; avoid speculative multi-version abstractions until two real targets need them.

## 4. Data, Resource, and Pack Format Pass

- [ ] Check `fabric.mod.json` dependency ranges and loader metadata.
- [ ] Check datapack/resource JSON schemas for the target Minecraft version.
- [ ] Check structure/template DataVersion compatibility for generated structures.
- [ ] Run `python tools/verify_repository.py` after every data/resource migration batch.

## 5. Test and QA Gates

- [ ] `python tools/verify_repository.py`
- [ ] `python -m unittest discover -s tests`
- [ ] `uv run --with pytest --with pillow -m pytest tests/ -q`
- [ ] `./gradlew test --no-daemon`
- [ ] `./gradlew build --no-daemon`
- [ ] Dedicated server smoke: `python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60`
- [ ] client smoke only when needed: `./gradlew runClient`.

## 6. Platform Metadata and Release Prep

- [ ] Update README/current docs only after the build and smoke tests pass.
- [ ] Update Modrinth game version metadata from `minecraft_version`.
- [ ] Update CurseForge `gameVersionNames` from `minecraft_version` and `java_version`.
- [ ] Keep Modrinth and CurseForge release notes neutral and current.
- [ ] Dry-run CurseForge metadata: `python tools/publish_curseforge.py --dry-run`.

## 7. Rollback / Backport Policy

- [ ] If the target cannot compile because dependencies are not available, revert only the profile application commit and keep the profile marked `blocked` with notes.
- [ ] Older-version support should be bugfix/backport-first, not a new-feature branch, unless the same feature is fully tested on all supported targets.
- [ ] Do not merge a port branch to `latest` until repository verification, Python tests, Gradle build, and smoke QA are green.
- [ ] After the work is complete and green, merge to `latest`, push `latest`, and watch GitHub CI to success.
