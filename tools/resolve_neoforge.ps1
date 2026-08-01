# Resolve the standard neoforge-family cherry-pick conflicts in the current worktree.
# Mirrors the resolution used on neoforge/26.2 (f0e7d28).
$ErrorActionPreference = "Stop"

function Resolve-Conflicts([string]$Path, [string]$Mode) {
    # Mode "theirs": keep the fc13ebd side of each conflict. Mode "ours": keep HEAD side.
    $lines = Get-Content $Path
    $out = New-Object System.Collections.Generic.List[string]
    $state = "normal"
    foreach ($l in $lines) {
        if ($l -match '^<{7}') { $state = "ours" }
        elseif ($l -match '^={7}$') { $state = "theirs" }
        elseif ($l -match '^>{7}') { $state = "normal" }
        elseif ($state -eq "normal" -or $state -eq $Mode) { $out.Add($l) }
    }
    ($out -join "`n") | Set-Content $Path -NoNewline
}

# 1. AttunedRegistries: take the cache-miss fix (theirs).
Resolve-Conflicts "src\main\java\dev\attuned\AttunedRegistries.java" "theirs"

# 2. FocusPreset: keep HEAD's simple model but bound the slots list decode.
Resolve-Conflicts "src\main\java\dev\attuned\attunement\FocusPreset.java" "ours"
$f = "src\main\java\dev\attuned\attunement\FocusPreset.java"
$c = Get-Content $f -Raw
$c = $c.Replace(
    "ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FocusPreset::slots,",
    "// Bounded: serverbound via ImportPresetPayload — a hacked client must`n`t`t`t// not be able to force allocation of an arbitrarily long slots list.`n`t`t`tByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(AttunedInv.SIZE)), FocusPreset::slots,")
Set-Content $f $c -NoNewline

# 3. UnseenCombat: per-victim pending procs, keeping HEAD's guard structure.
$f = "src\main\java\dev\attuned\combat\UnseenCombat.java"
Resolve-Conflicts $f "theirs"
$c = Get-Content $f -Raw
$c = $c.Replace(
    "boolean validCombatTarget = CombatTargets.isHostileOrPvpOpponent(defender, attacker);`n`t`tMap<UUID, PendingProc> pendingForAttacker =`n`t`t`tPENDING_NEEDLE.computeIfAbsent(attacker.getUUID(), id -> new HashMap<>());`n`t`tif (!hasActiveFocus(attacker, NEEDLE_FOCUS) || !validCombatTarget`n`t`t`t`t|| !canNeedle(attacker, defender, wasVeiled, pendingForAttacker)) {",
    "Map<UUID, PendingProc> pendingForAttacker =`n`t`t`tPENDING_NEEDLE.computeIfAbsent(attacker.getUUID(), id -> new HashMap<>());`n`t`tif (!hasActiveFocus(attacker, NEEDLE_FOCUS)`n`t`t`t`t|| !canNeedle(attacker, defender, wasVeiled, pendingForAttacker)) {")
Set-Content $f $c -NoNewline

# 4. FocusAbilityState: keep cooldowns across relog + /reload resync + prune.
$f = "src\main\java\dev\attuned\network\FocusAbilityState.java"
Resolve-Conflicts $f "theirs"
$c = Get-Content $f -Raw
$c = $c.Replace(
    "`t`t});`n`t`tAttunedPlayerCleanup.onForget(uuid -> {`n`t`t`tCOOLDOWNS.keySet().removeIf(key -> key.playerId().equals(uuid));`n`t`t`tLAST_SENT.remove(uuid);`n`t`t});`n`t}",
    "`t`t});`n`t`tAttunedServerCleanup.onStop(FocusAbilityState::clearCooldownState);`n`t`tAttunedPlayerCleanup.onForget(uuid -> {`n`t`t`tLAST_SENT.remove(uuid);`n`t`t});`n`t}`n`n`tprivate static void clearCooldownState() {`n`t`tCOOLDOWNS.clear();`n`t`tLAST_SENT.clear();`n`t}")
if ($c -notmatch "import net\.fabricmc\.fabric\.api\.event\.lifecycle\.v1\.ServerLifecycleEvents;") {
    $c = $c.Replace("import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;",
        "import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;`nimport net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;")
}
if ($c -notmatch "pruneExpiredCooldowns\(now\);") {
    $c = $c.Replace("`tprivate static void tick(MinecraftServer server) {`n`t`tlong now = server.overworld().getGameTime();`n`t`tif (now % SYNC_INTERVAL_TICKS != 0) {",
        "`tprivate static void tick(MinecraftServer server) {`n`t`tlong now = server.overworld().getGameTime();`n`t`tpruneExpiredCooldowns(now);`n`t`tif (now % SYNC_INTERVAL_TICKS != 0) {")
    $c = $c.Replace("`tprivate static boolean hasActiveAbility(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {",
        "`tprivate static void pruneExpiredCooldowns(long now) {`n`t`tCOOLDOWNS.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);`n`t}`n`n`tprivate static boolean hasActiveAbility(FocusBehavior behavior, ServerPlayer player, ItemStack stack) {")
}
Set-Content $f $c -NoNewline

# 5. UpdraftBehavior: gate flight before fall-distance reset; drop forced glide start.
$f = "src\main\java\dev\attuned\content\behavior\UpdraftBehavior.java"
Resolve-Conflicts $f "theirs"
$c = Get-Content $f -Raw
$c = $c.Replace(
    "`t`tif (!player.isFallFlying()) {`n`t`t`treturn;`n`t`t}`n`n`t`tplayer.resetFallDistance();",
    "`t`tif (!player.isFallFlying()) {`n`t`t`tsetControls(player.getUUID(), false, false);`n`t`t`treturn;`n`t`t}`n`n`t`tplayer.resetFallDistance();")
$c = $c -replace "(?s)\tprivate static boolean canStartGlide\(ServerPlayer player\) \{.*?\n\t\}\n\n", ""
$c = $c.Replace(
    "return isActive(player) && hasFunctionalElytra(player)`n`t`t`t&& controlsFor(player).active() && !isPvpExhausted(player);",
    "return isActive(player) && hasFunctionalElytra(player)`n`t`t`t&& player.isFallFlying() && controlsFor(player).active() && !isPvpExhausted(player);")
Set-Content $f $c -NoNewline

# 6. Tests: take the fix side, and fix RuntimeCleanup's FocusAbilityState assertion.
Resolve-Conflicts "src\test\java\dev\attuned\content\UpdraftFocusContractTest.java" "theirs"
Resolve-Conflicts "src\test\java\dev\attuned\network\FocusAbilityStateTest.java" "theirs"
$f = "src\test\java\dev\attuned\content\RuntimeCleanupContractTest.java"
if (Test-Path $f) {
    $c = Get-Content $f -Raw
    $c = $c.Replace('assertContains(read(FOCUS_ABILITY_STATE), "AttunedServerCleanup.onStop(() -> {");',
        'assertContains(read(FOCUS_ABILITY_STATE), "AttunedServerCleanup.onStop(FocusAbilityState::clearCooldownState)");')
    Set-Content $f $c -NoNewline
}

# 7. Client: only control an already active glide.
$f = "src\client\java\dev\attuned\client\UpdraftLiftClient.java"
if (Test-Path $f) {
    $c = Get-Content $f -Raw
    $c = $c.Replace(
        "return player.isFallFlying() || (!player.onGround() && !player.isInWater() && !player.isPassenger());",
        "// Only control an already active glide; boost packets must not be sent`n`t`t// merely because the player is airborne.`n`t`treturn player.isFallFlying();")
    Set-Content $f $c -NoNewline
}

Write-Host "RESOLVED"
