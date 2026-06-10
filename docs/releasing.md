# Releasing Attuned

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
