# Propagate fix commit from fabric/minecraft-26.2 to all maintenance branches sequentially.
param(
    [string]$FixCommit = "1fcddbbcb28c1ace2c34d8855929442a985726d7",
    [string]$RepoRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod",
    [string]$ResultsFile = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod\branch-fix-propagation.json"
)

$branches = @(
    "fabric/minecraft-1.18.2", "fabric/minecraft-1.19.2", "fabric/minecraft-1.19.4",
    "fabric/minecraft-1.20.1", "fabric/minecraft-1.20.6", "fabric/minecraft-1.21.1",
    "fabric/minecraft-1.21.11", "fabric/minecraft-26.1.2",
    "forge/1.18.2", "forge/1.19.2", "forge/1.19.4", "forge/1.20.1", "forge/1.20.6",
    "forge/1.21.1", "forge/1.21.11", "forge/26.1.2", "forge/26.2",
    "neoforge/1.20.6", "neoforge/1.21.1", "neoforge/1.21.11", "neoforge/26.1.2", "neoforge/26.2",
    "quilt/1.18.2", "quilt/1.19.2", "quilt/1.19.4", "quilt/1.20.1", "quilt/1.20.6",
    "quilt/1.21.1", "quilt/1.21.11", "quilt/26.1.2", "quilt/26.2",
    "latest"
)

function Resolve-ReloadConflicts($wt) {
    $att = Join-Path $wt "src/main/java/dev/attuned/attunement/Attunement.java"
    $reg = Join-Path $wt "src/main/java/dev/attuned/AttunedRegistries.java"
    foreach ($path in @($att, $reg)) {
        if (-not (Test-Path $path)) { continue }
        $text = Get-Content $path -Raw
        if ($text -notmatch '<<<<<<<') { continue }
        # Keep reload hook from incoming side; preserve branch-specific imports from HEAD
        $lines = Get-Content $path
        $out = New-Object System.Collections.Generic.List[string]
        $i = 0
        while ($i -lt $lines.Count) {
            if ($lines[$i] -match '^<<<<<<<') {
                $head = @(); $i++
                while ($i -lt $lines.Count -and $lines[$i] -notmatch '^=======') { $head += $lines[$i]; $i++ }
                $i++ # skip =======
                $incoming = @()
                while ($i -lt $lines.Count -and $lines[$i] -notmatch '^>>>>>>>') { $incoming += $lines[$i]; $i++ }
                $i++ # skip >>>>>>>
                $merged = @()
                foreach ($l in ($head + $incoming)) {
                    if ($l -match '^\s*import ' -and $merged -notcontains $l) { $merged += $l }
                }
                foreach ($l in ($head + $incoming)) {
                    if ($l -notmatch '^\s*import ') { $merged += $l }
                }
                # dedupe non-import lines by keeping incoming body for init/static blocks when present
                $body = ($incoming | Where-Object { $_ -notmatch '^\s*import ' }) -join "`n"
                $headImports = ($head | Where-Object { $_ -match '^\s*import ' })
                $inImports = ($incoming | Where-Object { $_ -match '^\s*import ' })
                $allImports = ($headImports + $inImports | Select-Object -Unique)
                $nonImportHead = $head | Where-Object { $_ -notmatch '^\s*import ' }
                $nonImportIn = $incoming | Where-Object { $_ -notmatch '^\s*import ' }
                # Prefer incoming for init/reload additions; use simpler merge: all imports + incoming non-import if has ServerLifecycleEvents
                if (($incoming -join "`n") -match 'ServerLifecycleEvents') {
                    foreach ($imp in $allImports) { $out.Add($imp) }
                    foreach ($nl in $nonImportIn) { $out.Add($nl) }
                } else {
                    foreach ($imp in $allImports) { $out.Add($imp) }
                    foreach ($nl in $nonImportHead) { $out.Add($nl) }
                }
            } else {
                $out.Add($lines[$i])
                $i++
            }
        }
        Set-Content -Path $path -Value ($out -join "`n") -NoNewline
        Add-Content -Path $path -Value ""
    }
}

$results = @()
Set-Location $RepoRoot

foreach ($branch in $branches) {
    Write-Host "`n========== $branch ==========" -ForegroundColor Cyan
    $entry = [ordered]@{ branch = $branch; status = "pending"; error = $null }
    try {
        & "$RepoRoot\tools\port_fix_branch.ps1" -Branch $branch -Mode pick -FixCommit $FixCommit 2>&1 | Tee-Object -Variable pickOut
        $pickText = $pickOut | Out-String
        if ($pickText -match 'CONFLICTS:') {
            $override = @{
                "quilt/1.19.2"="quilt-1.19.2"; "quilt/1.20.6"="quilt-1-20-6"; "quilt/1.18.2"="quilt-1-18-2"
                "quilt/1.19.4"="quilt-1-19-4"; "quilt/1.20.1"="quilt-1-20-1"; "quilt/1.21.1"="quilt-1-21-1"
                "quilt/1.21.11"="quilt-1-21-11"; "quilt/26.1.2"="quilt-26-1-2"; "quilt/26.2"="quilt-26-2"
                "forge/1.18.2"="forge-1-18-2-fix"
            }
            $safe = if ($override.ContainsKey($branch)) { $override[$branch] } else { $branch -replace '[/\\]','-' }
            $wt = Join-Path "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\attuned-worktrees" $safe
            Resolve-ReloadConflicts $wt
            git -C $wt add -A
            $conf = git -C $wt diff --name-only --diff-filter=U
            if ($conf) {
                $entry.status = "conflict"
                $entry.error = ($conf -join ", ")
                Write-Host "UNRESOLVED: $($entry.error)" -ForegroundColor Red
                git -C $wt cherry-pick --abort 2>$null
                $results += New-Object psobject -Property $entry
                continue
            }
        }
        & "$RepoRoot\tools\port_fix_branch.ps1" -Branch $branch -Mode finish -FixCommit $FixCommit 2>&1 | Tee-Object -Variable finishOut
        if ($LASTEXITCODE -eq 0) {
            $entry.status = "pass"
            Write-Host "OK $branch" -ForegroundColor Green
        } else {
            $entry.status = "fail"
            $entry.error = ($finishOut | Select-Object -Last 5 | Out-String)
            Write-Host "FAIL $branch" -ForegroundColor Red
            git -C (Join-Path "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\attuned-worktrees" ($branch -replace '[/\\]','-')) cherry-pick --abort 2>$null
        }
    } catch {
        $entry.status = "error"
        $entry.error = $_.Exception.Message
    }
    $results += New-Object psobject -Property $entry
}

$results | ConvertTo-Json -Depth 3 | Set-Content $ResultsFile
Write-Host "`nResults: $ResultsFile" -ForegroundColor Yellow
($results | Group-Object status | ForEach-Object { "$($_.Name): $($_.Count)" }) -join ", "
