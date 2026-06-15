# Releasing Attuned

## Platform descriptions

Before publishing a release, keep the long-form project descriptions in sync with
`README.md` and the current `CHANGELOG.md` section:

- Modrinth project description source: `docs/platform/modrinth-description.md`
- CurseForge project description source: `docs/platform/curseforge-description.md`
- Platform release-notes copy for the current release: `docs/platform/attuned-1.5.0-release-notes.md`

Updating these files does **not** update the public project pages by itself. Paste
or upload the matching description during the platform release step, and do not
publish or mutate public pages unless that release action is explicitly requested.

## CurseForge

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
   python tools\publish_curseforge.py --dry-run
   ```

4. Upload:

   ```powershell
   python tools\publish_curseforge.py
   ```

The script uploads `build/libs/attuned-<mod_version>.jar` to CurseForge project
`1553444`, uses the matching `CHANGELOG.md` section, marks the file as a
release, tags it for Minecraft/Fabric/Java/client/server, and declares Fabric
API as a required dependency.
