# Propagate 1fcddbb fix commit to all branches (except fabric/minecraft-26.2).
param(
    [string]$FixCommit = "1fcddbbcb28c1ace2c34d8855929442a985726d7",
    [string]$RepoRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod",
    [string]$WorktreeRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\attuned-worktrees",
    [string]$ResultsFile = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod\branch-fix-propagation.json"
)

$override = @{
    "quilt/1.19.2"="quilt-1.19.2"; "quilt/1.20.6"="quilt-1-20-6"; "quilt/1.18.2"="quilt-1-18-2"
    "quilt/1.19.4"="quilt-1-19-4"; "quilt/1.20.1"="quilt-1-20-1"; "quilt/1.21.1"="quilt-1-21-1"
    "quilt/1.21.11"="quilt-1-21-11"; "quilt/26.1.2"="quilt-26-1-2"; "quilt/26.2"="quilt-26-2"
    "forge/1.18.2"="forge-1-18-2-fix"
}

$branches = @(
    "fabric/minecraft-1.18.2", "fabric/minecraft-1.19.2", "fabric/minecraft-1.19.4",
    "fabric/minecraft-1.20.1", "fabric/minecraft-1.20.6", "fabric/minecraft-1.21.11",
    "fabric/minecraft-26.1.2",
    "forge/1.18.2", "forge/1.19.2", "forge/1.19.4", "forge/1.20.1", "forge/1.20.6",
    "forge/1.21.1", "forge/1.21.11", "forge/26.1.2", "forge/26.2",
    "neoforge/1.20.6", "neoforge/1.21.1", "neoforge/1.21.11", "neoforge/26.1.2", "neoforge/26.2",
    "quilt/1.18.2", "quilt/1.19.2", "quilt/1.19.4", "quilt/1.20.1", "quilt/1.20.6",
    "quilt/1.21.1", "quilt/1.21.11", "quilt/26.1.2", "quilt/26.2",
    "latest"
)

function Get-Wt($branch) {
    $safe = if ($override.ContainsKey($branch)) { $override[$branch] } else { $branch -replace '[/\\]','-' }
    Join-Path $WorktreeRoot $safe
}

function Resolve-MergeConflicts($wt) {
    foreach ($rel in @(
        "src/main/java/dev/attuned/attunement/Attunement.java",
        "src/main/java/dev/attuned/AttunedRegistries.java"
    )) {
        $path = Join-Path $wt $rel
        if (-not (Test-Path $path)) { continue }
        $raw = Get-Content $path -Raw
        if ($raw -notmatch '<<<<<<<') { continue }
        $head = @(); $incoming = @(); $mode = 'head'
        foreach ($line in (Get-Content $path)) {
            if ($line -match '^<<<<<<<') { $mode = 'head'; continue }
            if ($line -match '^=======') { $mode = 'incoming'; continue }
            if ($line -match '^>>>>>>>') { $mode = 'done'; continue }
            if ($mode -eq 'head') { $head += $line }
            elseif ($mode -eq 'incoming') { $incoming += $line }
        }
        $imports = ($head + $incoming | Where-Object { $_ -match '^\s*import ' } | Select-Object -Unique)
        $body = $incoming | Where-Object { $_ -notmatch '^\s*import ' }
        if (-not ($body -join "`n" -match 'ServerLifecycleEvents')) {
            $body = $head | Where-Object { $_ -notmatch '^\s*import ' }
        }
        ($imports + $body) -join "`n" | Set-Content $path -NoNewline
        Add-Content $path ""
    }
}

function Adapt-UpdraftForBranch($wt) {
    $server = Join-Path $wt "src/main/java/dev/attuned/content/behavior/UpdraftBehavior.java"
    $client = Join-Path $wt "src/client/java/dev/attuned/client/UpdraftLiftClient.java"
    $test = Join-Path $wt "src/test/java/dev/attuned/content/UpdraftFocusContractTest.java"
    if (-not (Test-Path $server) -or -not (Test-Path $client)) { return }
    $serverText = Get-Content $server -Raw
    if ($serverText -match 'DataComponents\.GLIDER') { return }
    if ($serverText -notmatch 'ElytraItem') { return }
    $c = Get-Content $client -Raw
    $c = $c -replace 'import net\.minecraft\.core\.component\.DataComponents;\r?\n', ''
    if ($c -notmatch 'ElytraItem') {
        $c = $c -replace '(import net\.minecraft\.world\.entity\.player\.Player;)', "`$1`nimport net.minecraft.world.item.ElytraItem;"
    }
    $c = $c -replace '(?s)private static boolean hasFunctionalElytra\(Player player\) \{.*?\n\t\}', @'
	private static boolean hasFunctionalElytra(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof ElytraItem)) {
			return false;
		}
		if (!chest.isDamageableItem()) {
			return true;
		}
		return chest.getDamageValue() < chest.getMaxDamage() - 1;
	}
'@
    Set-Content $client $c -NoNewline
    if (Test-Path $test) {
        $t = Get-Content $test -Raw
        $t = $t -replace 'DataComponents\.GLIDER', 'ElytraItem'
        $t = $t -replace 'server glider data-component probe', 'server ElytraItem probe on pre-glider MC versions'
        Set-Content $test $t -NoNewline
    }
}

$results = @()
Set-Location $RepoRoot

foreach ($branch in $branches) {
    Write-Host "`n===== $branch =====" -ForegroundColor Cyan
    $entry = [ordered]@{ branch = $branch; status = "pending"; error = $null }
    $wt = Get-Wt $branch
    try {
        & "$RepoRoot\tools\port_fix_branch.ps1" -Branch $branch -Mode pick -FixCommit $FixCommit 2>&1 | Out-String | Write-Host
        if (-not (Test-Path $wt)) { throw "worktree missing: $wt" }
        Resolve-MergeConflicts $wt
        Adapt-UpdraftForBranch $wt
        $conf = git -C $wt diff --name-only --diff-filter=U
        if ($conf) {
            $entry.status = "conflict"; $entry.error = ($conf -join ", ")
            git -C $wt checkout -- . 2>$null; git -C $wt cherry-pick --abort 2>$null
            $results += New-Object psobject -Property $entry; continue
        }
        git -C $wt add -A
        & "$RepoRoot\tools\port_fix_branch.ps1" -Branch $branch -Mode finish -FixCommit $FixCommit 2>&1 | Tee-Object -Variable out
        if ($LASTEXITCODE -eq 0) {
            $entry.status = "pass"
        } else {
            $entry.status = "fail"
            $entry.error = ($out | Select-Object -Last 8 | Out-String)
            git -C $wt cherry-pick --abort 2>$null
        }
    } catch {
        $entry.status = "error"; $entry.error = $_.Exception.Message
        git -C $wt cherry-pick --abort 2>$null
    }
    $results += New-Object psobject -Property $entry
}

$results | ConvertTo-Json -Depth 3 | Set-Content $ResultsFile
Write-Host "`nSaved $ResultsFile"
$results | Group-Object status | ForEach-Object { Write-Host "$($_.Name): $($_.Count)" }
