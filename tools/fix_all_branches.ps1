# Sequential fix + test all branches — no commits
param(
    [string]$RepoRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod",
    [string]$WorktreeRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\attuned-worktrees",
    [string]$ResultsFile = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod\branch-test-results-final.json"
)

$Jdk17 = "C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot"
$Jdk21 = "C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
$Jdk25 = "C:/Program Files/Microsoft/jdk-25.0.3.9-hotspot"
$ToolchainPaths = "$Jdk17,$Jdk21,$Jdk25"
$GradleArgs = "-Dorg.gradle.java.installations.paths=$ToolchainPaths"

function Safe-Name($s) { $s -replace '[/\\]', '-' }

function Get-RuntimeJdk($javaVer, $loader) {
    $v = [int]$javaVer
    if ($loader -eq "forge" -and $v -le 17) { return "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot" }
    if ($v -le 17) { return "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
    if ($v -le 21) { return "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
    return "C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
}

$localWtOverride = @{
    "quilt/1.19.2" = "quilt-1.19.2"
    "quilt/1.20.6" = "quilt-1-20-6"
    "quilt/1.18.2" = "quilt-1-18-2"
    "quilt/1.19.4" = "quilt-1-19-4"
    "quilt/1.20.1" = "quilt-1-20-1"
    "quilt/1.21.1" = "quilt-1-21-1"
    "quilt/1.21.11" = "quilt-1-21-11"
    "quilt/26.1.2" = "quilt-26-1-2"
    "quilt/26.2" = "quilt-26-2"
    "forge/1.18.2" = "forge-1-18-2-fix"
}

$targets = @(
    @{ branch = "fabric/minecraft-1.18.2"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-1.19.2"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-1.19.4"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-1.20.1"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-1.20.6"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-1.21.1"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-1.21.11"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-26.1.2"; local = $null; loader = "fabric" },
    @{ branch = "fabric/minecraft-26.2"; local = $null; loader = "fabric"; skipCheckout = $true },
    @{ branch = "forge/1.18.2"; local = "forge/1.18.2"; loader = "forge"; skipCheckout = $true },
    @{ branch = "forge/1.19.2"; local = $null; loader = "forge"; skipCheckout = $true },
    @{ branch = "forge/1.19.4"; local = $null; loader = "forge"; skipCheckout = $true },
    @{ branch = "forge/1.20.1"; local = $null; loader = "forge"; skipCheckout = $true },
    @{ branch = "forge/1.20.6"; local = $null; loader = "forge" },
    @{ branch = "forge/1.21.1"; local = $null; loader = "forge" },
    @{ branch = "forge/1.21.11"; local = $null; loader = "forge" },
    @{ branch = "forge/26.1.2"; local = $null; loader = "forge" },
    @{ branch = "forge/26.2"; local = $null; loader = "forge" },
    @{ branch = "neoforge/1.20.6"; local = $null; loader = "neoforge" },
    @{ branch = "neoforge/1.21.1"; local = $null; loader = "neoforge" },
    @{ branch = "neoforge/1.21.11"; local = $null; loader = "neoforge" },
    @{ branch = "neoforge/26.1.2"; local = $null; loader = "neoforge" },
    @{ branch = "neoforge/26.2"; local = $null; loader = "neoforge" },
    @{ branch = "quilt/1.19.2"; local = "quilt/1.19.2"; loader = "quilt" },
    @{ branch = "quilt/1.20.6"; local = "quilt/1.20.6"; loader = "quilt" },
    @{ branch = "quilt/1.18.2"; local = "quilt/1.18.2"; loader = "quilt" },
    @{ branch = "quilt/1.19.4"; local = "quilt/1.19.4"; loader = "quilt" },
    @{ branch = "quilt/1.20.1"; local = "quilt/1.20.1"; loader = "quilt" },
    @{ branch = "quilt/1.21.1"; local = "quilt/1.21.1"; loader = "quilt" },
    @{ branch = "quilt/1.21.11"; local = "quilt/1.21.11"; loader = "quilt" },
    @{ branch = "quilt/26.1.2"; local = "quilt/26.1.2"; loader = "quilt" },
    @{ branch = "quilt/26.2"; local = "quilt/26.2"; loader = "quilt" },
    @{ branch = "latest"; local = $null; loader = "fabric" }
)

$results = @()
Set-Location $RepoRoot
git fetch --all --quiet 2>&1 | Out-Null

foreach ($t in $targets) {
    $name = $t.branch
    Write-Host "`n===== $name =====" -ForegroundColor Cyan
    $entry = [ordered]@{ branch = $name; status = "pending"; testsPassed = $null; testsFailed = $null; error = $null }

    try {
        $safe = if ($t.local -and $localWtOverride.ContainsKey($t.local)) { $localWtOverride[$t.local] } else { Safe-Name $(if ($t.local) { $t.local } else { $name }) }
        $wt = Join-Path $WorktreeRoot $safe

        if (-not (Test-Path $wt)) {
            if ($t.local) { git worktree add $wt $t.local 2>&1 | Out-Null }
            else { git worktree add $wt "origin/$name" 2>&1 | Out-Null }
        } elseif ($t.skipCheckout) {
            Write-Host "Keeping worktree as-is (uncommitted fixes)" -ForegroundColor DarkYellow
        } elseif ($t.local) {
            git -C $wt checkout $t.local 2>&1 | Out-Null
        } else {
            git -C $wt fetch origin $name 2>&1 | Out-Null
            git -C $wt checkout --detach "origin/$name" 2>&1 | Out-Null
        }

        $gp = Get-Content (Join-Path $wt "gradle.properties")
        $jv = ($gp | Where-Object { $_ -match "^java_version=" }) -replace "java_version=", ""
        $runtime = Get-RuntimeJdk $jv $t.loader
        $env:JAVA_HOME = $runtime
        $env:Path = "$env:JAVA_HOME\bin;" + [System.Environment]::GetEnvironmentVariable("Path","Machine")

        Write-Host "Refresh verification (JDK $jv runtime $runtime)..." -ForegroundColor DarkGray
        Push-Location $wt
        & .\gradlew.bat --write-verification-metadata sha256 help --no-daemon $GradleArgs 2>&1 | Out-Null
        $out = & .\gradlew.bat test --no-daemon $GradleArgs 2>&1 | Out-String
        Pop-Location

        if ($out -match "(\d+) tests completed, (\d+) failed") {
            $entry.testsPassed = [int]$Matches[1] - [int]$Matches[2]
            $entry.testsFailed = [int]$Matches[2]
        } elseif ($out -match "(\d+) tests completed") {
            $entry.testsPassed = [int]$Matches[1]; $entry.testsFailed = 0
        }

        if ($LASTEXITCODE -eq 0) {
            $entry.status = "pass"
            Write-Host "PASS $($entry.testsPassed) tests" -ForegroundColor Green
        } else {
            $entry.status = "fail"
            $entry.error = ($out -split "`n" | Where-Object { $_ -match "FAILED|What went wrong|AssertionFailed|verification failed" } | Select-Object -Last 5) -join "`n"
            Write-Host "FAIL" -ForegroundColor Red
        }
    } catch {
        $entry.status = "error"; $entry.error = $_.Exception.Message
        Write-Host "ERROR $($_.Exception.Message)" -ForegroundColor Red
    }

    $results += [pscustomobject]$entry
    $results | ConvertTo-Json -Depth 4 | Set-Content $ResultsFile -Encoding UTF8
}

$pass = ($results | Where-Object { $_.status -eq "pass" }).Count
Write-Host "`nFINAL: $pass / $($results.Count) passed" -ForegroundColor Yellow
