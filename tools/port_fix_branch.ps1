# Port the 26.2 gameplay-fix commit to one branch: pick | finish
param(
    [Parameter(Mandatory)][string]$Branch,
    [Parameter(Mandatory)][ValidateSet("pick","finish","test")][string]$Mode,
    [string]$FixCommit = "fc13ebda08cd965f84c22048e048cd1ca1e03408",
    [string]$RepoRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod",
    [string]$WorktreeRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\attuned-worktrees"
)
$ErrorActionPreference = "Continue"
$Jdk17 = "C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot"
$Jdk21 = "C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
$Jdk25 = "C:/Program Files/Microsoft/jdk-25.0.3.9-hotspot"
$GradleArgs = "-Dorg.gradle.java.installations.paths=$Jdk17,$Jdk21,$Jdk25"

$override = @{
    "quilt/1.19.2"="quilt-1.19.2"; "quilt/1.20.6"="quilt-1-20-6"; "quilt/1.18.2"="quilt-1-18-2"
    "quilt/1.19.4"="quilt-1-19-4"; "quilt/1.20.1"="quilt-1-20-1"; "quilt/1.21.1"="quilt-1-21-1"
    "quilt/1.21.11"="quilt-1-21-11"; "quilt/26.1.2"="quilt-26-1-2"; "quilt/26.2"="quilt-26-2"
    "forge/1.18.2"="forge-1-18-2-fix"
}
$safe = if ($override.ContainsKey($Branch)) { $override[$Branch] } else { $Branch -replace '[/\\]','-' }
$wt = Join-Path $WorktreeRoot $safe

if (-not (Test-Path $wt)) {
    Set-Location $RepoRoot
    git worktree add $wt --detach "origin/$Branch"
    if ($LASTEXITCODE -ne 0) { Write-Host "WORKTREE-ADD-FAILED"; exit 1 }
}
Set-Location $wt

if ($Mode -eq "pick") {
    # attach local branch
    $hasLocal = (git branch --list $Branch | Measure-Object).Count -gt 0
    if ($hasLocal) { git switch $Branch } else { git switch -c $Branch --track "origin/$Branch" }
    if ($LASTEXITCODE -ne 0) { Write-Host "SWITCH-FAILED"; exit 1 }
    $dirty = git status --porcelain | Where-Object { $_ -notmatch "gradle/verification-metadata.xml" }
    if ($dirty) { Write-Host "DIRTY-WORKTREE:"; $dirty; exit 1 }
    # discard uncommitted metadata noise; the test phase regenerates it
    git checkout -- gradle/verification-metadata.xml
    git cherry-pick --no-commit $FixCommit
    # never take the 26.2 verification metadata; keep branch's own
    git checkout HEAD -- "gradle/verification-metadata.xml"
    $conf = git diff --name-only --diff-filter=U
    if ($conf) { Write-Host "CONFLICTS:"; $conf } else { Write-Host "PICK-CLEAN" }
    git status --short
    exit 0
}

# test / finish: set JDK from gradle.properties
$jv = [int]((Get-Content "gradle.properties" | Where-Object { $_ -match "^java_version=" }) -replace "java_version=","")
$loader = ($Branch -split "/")[0]
if ($loader -eq "forge" -and $jv -le 17) { $rt = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot" }
elseif ($jv -le 21) { $rt = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
else { $rt = "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot" }
$env:JAVA_HOME = $rt
$env:Path = "$rt\bin;" + [System.Environment]::GetEnvironmentVariable("Path","Machine")
Write-Host "Using JDK runtime: $rt (java_version=$jv)"

& .\gradlew.bat --write-verification-metadata sha256 help --no-daemon $GradleArgs 2>&1 | Select-Object -Last 3
$out = & .\gradlew.bat test --no-daemon $GradleArgs 2>&1 | Out-String
$testExit = $LASTEXITCODE
if ($out -match "(\d+) tests completed(, (\d+) failed)?") { Write-Host "TESTS: $($Matches[0])" }
if ($testExit -ne 0) {
    Write-Host "TEST-FAILED"
    ($out -split "`n" | Where-Object { $_ -match "FAILED|error:|What went wrong|cannot find symbol" } | Select-Object -First 25)
    exit 1
}
Write-Host "TEST-PASS"
if ($Mode -eq "finish") {
    git add -A
    git commit -m "fix: backport gameplay bug fixes from 26.2"
    if ($LASTEXITCODE -ne 0) { Write-Host "COMMIT-FAILED"; exit 1 }
    git log -1 --format='%h %s'
    git push -u origin $Branch
    if ($LASTEXITCODE -ne 0) { Write-Host "PUSH-FAILED"; exit 1 }
    Write-Host "PUSHED"
}
