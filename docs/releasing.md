# Releasing Attuned

## Platform descriptions

Before publishing a release, keep the long-form project descriptions in sync with
`README.md` and the current `CHANGELOG.md` section:

- Modrinth project description source: `docs/platform/modrinth-description.md`
- CurseForge project description source: `docs/platform/curseforge-description.md`
- Platform release-notes copy for the current release: `docs/platform/attuned-1.5.2-release-notes.md`

Updating these files does **not** update the public project pages by itself. Paste
or upload the matching description during the platform release step, and do not
publish or mutate public pages unless that release action is explicitly requested.

## Loader-aware release rule

The current automated release path publishes the Fabric artifact only. Quilt
compatibility, Quilt-native, Forge, and NeoForge releases must have separate
loader evidence: dependency declarations, smoke-test evidence, platform tags,
hands-on HUD smoke, and release copy. Do not reuse the Fabric upload metadata
for a Quilt, NeoForge, or Forge file.

See `docs/loader-support.md` and
`docs/superpowers/plans/2026-06-25-loader-port-roadmap.md` for the detailed
loader release requirements.

## CurseForge - current Fabric artifact

1. Build and verify the jar:

   ```powershell
   .\gradlew.bat build --no-daemon
   ```

2. Set the CurseForge Authors API token from the old Authors API Tokens page.
   Do not use the CurseForge Studios server key.

   ```powershell
   $env:CURSEFORGE_TOKEN = '<authors-api-token>'
   ```

3. Dry-run the upload metadata:

   ```powershell
   python tools\publish_curseforge.py --dry-run --loader fabric
   ```

4. Upload:

   ```powershell
   python tools\publish_curseforge.py --loader fabric
   ```

The script uploads `build/libs/attuned-<mod_version>.jar` to CurseForge project
`1553444`, uses the matching `CHANGELOG.md` section, marks the file as a
release, tags it for Minecraft/Fabric/Java/client/server, and declares Fabric
API as a required dependency.

Future Quilt-native, NeoForge, and Forge uploads must pass `--loader quilt`,
`--loader neoforge`, or `--loader forge` only after that loader's dedicated
artifact and checklist evidence exist. The `quilt-compat` track remains runtime
compatibility evidence for the Fabric jar, not a separate upload tag.
