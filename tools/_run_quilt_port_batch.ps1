$FixCommit = "1fcddbbcb28c1ace2c34d8855929442a985726d7"
$RepoRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\Attuned Minecraft Mod"
$WorktreeRoot = "C:\Users\Demon\OneDrive\Desktop\Minecraft Stuff\attuned-worktrees"
$PortScript = Join-Path $RepoRoot "tools\port_fix_branch.ps1"
$OutJson = Join-Path $RepoRoot "branch-fix-propagation-quilt.json"
$WtOverride = @{
  "quilt/1.19.2"="quilt-1.19.2"; "quilt/1.20.6"="quilt-1-20-6"; "quilt/1.18.2"="quilt-1-18-2"
  "quilt/1.19.4"="quilt-1-19-4"; "quilt/1.20.1"="quilt-1-20-1"; "quilt/1.21.1"="quilt-1-21-1"
  "quilt/1.21.11"="quilt-1-21-11"; "quilt/26.1.2"="quilt-26-1-2"; "quilt/26.2"="quilt-26-2"
}
$Branches = @(
  "quilt/1.18.2", "quilt/1.19.2", "quilt/1.19.4", "quilt/1.20.1", "quilt/1.20.6",
  "quilt/1.21.1", "quilt/1.21.11", "quilt/26.1.2", "quilt/26.2"
)
function Get-WtPath([string]$Branch) {
  $safe = if ($WtOverride.ContainsKey($Branch)) { $WtOverride[$Branch] } else { $Branch -replace '[/\\]','-' }
  Join-Path $WorktreeRoot $safe
}
function Test-AlreadyPorted([string]$Branch) {
  $msg = git -C $RepoRoot log "origin/$Branch" --oneline -15 2>$null
  if ($msg -match "fix: backport gameplay bug fixes from 26.2") { return "backport_commit_exists" }
  if ($msg -match "1fcddbb") { return "fix_commit_on_branch" }
  return $null
}
function Resolve-RegistryConflicts([string]$Wt) {
  foreach ($rel in @("src/main/java/dev/attuned/AttunedRegistries.java","src/main/java/dev/attuned/attunement/Attunement.java")) {
    $f = Join-Path $Wt $rel
    if (-not (Test-Path $f)) { continue }
    $c = Get-Content $f -Raw
    if ($c -notmatch '<<<<<<<') { continue }
    Write-Host "Manual merge for $rel"
    # read both sides from conflict markers - simplified: take incoming for ServerLifecycleEvents block and merge imports
    $lines = Get-Content $f
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
        $merged = @($head + $incoming) | Where-Object { $_ -match '\S' }
        # dedupe import lines and keep unique non-import lines in order
        $imports = [ordered]@{}
        $body = New-Object System.Collections.Generic.List[string]
        foreach ($ln in $merged) {
          if ($ln -match '^\s*import\s+') { $imports[$ln.Trim()] = $true }
          else { $body.Add($ln) }
        }
        # rebuild not ideal for mid-file conflicts - use union of imports + prefer incoming for non-import duplicate blocks
        foreach ($imp in $imports.Keys) { $out.Add($imp) }
        foreach ($ln in $body) { $out.Add($ln) }
      } else {
        $out.Add($lines[$i])
        $i++
      }
    }
    # Better approach: use git checkout --ours for structure then add missing from theirs
  }
}
function Fix-ElytraClient([string]$Wt) {
  $dataBeh = Join-Path $Wt "src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java"
  if (-not (Test-Path $dataBeh)) { return $false }
  $db = Get-Content $dataBeh -Raw
  if ($db -match 'DataComponents\.GLIDER') { return $false }
  if ($db -notmatch 'ElytraItem') { return $false }
  Write-Host "Applying ElytraItem client/test fixes"
  $client = Join-Path $Wt "src/client/java/dev/attuned/client/UpdraftLiftClient.java"
  if (Test-Path $client) {
    $cc = Get-Content $client -Raw
    $cc = $cc -replace 'DataComponents\.GLIDER', 'ElytraItem'
    if ($cc -notmatch 'import net\.minecraft\.world\.item\.ElytraItem') {
      $cc = $cc -replace '(import net\.minecraft\.[^\r\n]+;\r?\n)', "`$1import net.minecraft.world.item.ElytraItem;`n", 1
    }
    $cc = $cc -replace 'import net\.minecraft\.core\.component\.DataComponents;\r?\n', ''
    Set-Content $client $cc -NoNewline
  }
  $test = Join-Path $Wt "src/test/java/dev/attuned/content/UpdraftFocusContractTest.java"
  if (Test-Path $test) {
    $tc = Get-Content $test -Raw
    $tc = $tc -replace 'DataComponents\.GLIDER', 'ElytraItem'
    if ($tc -notmatch 'import net\.minecraft\.world\.item\.ElytraItem') {
      $tc = $tc -replace '(import net\.minecraft\.[^\r\n]+;\r?\n)', "`$1import net.minecraft.world.item.ElytraItem;`n", 1
    }
    $tc = $tc -replace 'import net\.minecraft\.core\.component\.DataComponents;\r?\n', ''
    Set-Content $test $tc -NoNewline
  }
  return $true
}
function Resolve-ConflictsSmart([string]$Wt) {
  $conf = git -C $Wt diff --name-only --diff-filter=U 2>$null
  if ($conf) {
    foreach ($rel in @($conf)) {
      git -C $Wt cat-file -e "HEAD:$rel" 2>$null
      if ($LASTEXITCODE -ne 0) {
        git -C $Wt rm -f -- $rel 2>$null
      }
    }
  }
  $conf = git -C $Wt diff --name-only --diff-filter=U 2>$null
  if (-not $conf) { return $true }
  foreach ($rel in $conf) {
    $f = Join-Path $Wt $rel
    $text = Get-Content $f -Raw
    if ($rel -match 'AttunedRegistries\.java|Attunement\.java') {
      # Union merge: collect all import lines from both sides, use incoming for code body where possible
      $parts = $text -split '<<<<<<< HEAD', 0
      if ($parts.Count -lt 2) { continue }
      $result = $parts[0]
      for ($p = 1; $p -lt $parts.Count; $p++) {
        $chunk = $parts[$p]
        if ($chunk -notmatch '=======([\s\S]*?)>>>>>>>') { continue }
        $headPart = ($chunk -split '=======', 2)[0]
        $rest = ($chunk -split '=======', 2)[1]
        $incomingPart = ($rest -split '>>>>>>>', 2)[0]
        $after = ($rest -split '>>>>>>>', 2)[1]
        $allLines = @($headPart.Split("`n") + $incomingPart.Split("`n")) | ForEach-Object { $_.TrimEnd("`r") }
        $importSet = [ordered]@{}
        $nonImport = New-Object System.Collections.Generic.List[string]
        foreach ($ln in $allLines) {
          if ($ln -match '^\s*import\s+') { $importSet[$ln.Trim()] = $true }
          elseif ($ln -match '^\s*$') { }
          else { $nonImport.Add($ln) }
        }
        # For registry files: prefer incoming non-import lines but ensure ServerLifecycleEvents import exists
        $importSet['import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;'] = $true
        $mergedBlock = ($importSet.Keys | Sort-Object) + $nonImport
        $result += ($mergedBlock -join "`n") + $after
      }
      Set-Content $f ($result.TrimEnd() + "`n") -NoNewline
      git -C $Wt add $rel
    } else {
      # try incoming (theirs from cherry-pick is staged as other?)
      git -C $Wt checkout --theirs $rel 2>$null
      if ($LASTEXITCODE -ne 0) { git -C $Wt checkout --ours $rel 2>$null }
      git -C $Wt add $rel
    }
  }
  $still = git -C $Wt diff --name-only --diff-filter=U
  if ($still) { Write-Host "Unresolved: $still"; return $false }
  return $true
}
function Invoke-PortBranch([string]$Branch, [bool]$Retry) {
  $wt = Get-WtPath $Branch
  $r = @{ branch = $Branch; status = "fail"; detail = ""; retry = $Retry }
  $skip = Test-AlreadyPorted $Branch
  if ($skip) { $r.status = "pass"; $r.detail = $skip; return $r }
  Write-Host "`n######## $Branch ########"
  & $PortScript -Branch $Branch -Mode pick -FixCommit $FixCommit 2>&1 | Tee-Object -Variable pickOut
  $pickText = $pickOut | Out-String
  if ($pickText -match 'DIRTY-WORKTREE|SWITCH-FAILED|WORKTREE-ADD-FAILED') {
    $r.detail = ($pickText -split "`n" | Select-Object -First 5) -join ' '
    return $r
  }
  if ($pickText -match 'CONFLICTS:') {
    if (-not (Resolve-ConflictsSmart $wt)) {
      $r.detail = "conflict resolution failed"
      return $r
    }
  }
  Fix-ElytraClient $wt | Out-Null
  git -C $wt add -A
  & $PortScript -Branch $Branch -Mode finish -FixCommit $FixCommit 2>&1 | Tee-Object -Variable finOut
  $finText = $finOut | Out-String
  if ($finText -match 'PUSHED') { $r.status = "pass"; $r.detail = "pushed"; return $r }
  if ($finText -match 'TEST-PASS' -and $finText -match 'nothing to commit') { $r.status = "pass"; $r.detail = "already_applied"; return $r }
  if ($finText -match 'TEST-FAILED|COMMIT-FAILED|PUSH-FAILED') {
    $r.detail = ($finText -split "`n" | Where-Object { $_ -match 'FAILED|error:|cannot find symbol|What went wrong' } | Select-Object -First 8) -join '; '
    return $r
  }
  $r.detail = "unknown finish outcome"
  return $r
}
$env:GIT_AUTHOR_NAME = "defnotean"
$env:GIT_AUTHOR_EMAIL = "iangaleonofficial@gmail.com"
$env:GIT_COMMITTER_NAME = "defnotean"
$env:GIT_COMMITTER_EMAIL = "iangaleonofficial@gmail.com"
Set-Location $RepoRoot
git fetch origin 2>&1 | Out-Null
$results = @()
foreach ($b in $Branches) {
  $res = Invoke-PortBranch $b $false
  if ($res.status -eq 'fail') {
    Write-Host "Retrying $b after failure..."
    git -C (Get-WtPath $b) cherry-pick --abort 2>$null
    git -C (Get-WtPath $b) reset --hard "origin/$b" 2>$null
    $res2 = Invoke-PortBranch $b $true
    $res2.first_attempt = $res.detail
    $results += $res2
  } else {
    $results += $res
  }
}
$results | ConvertTo-Json -Depth 5 | Set-Content $OutJson -Encoding UTF8
Write-Host "`nRESULTS:"
$results | ForEach-Object { Write-Host "$($_.branch): $($_.status) - $($_.detail)" }
