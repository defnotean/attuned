# Foci HUD Apex Cooldown Implementation Plan

> **Implementation:** Work tasks in order using the checkbox (`- [ ]`) syntax below.

**Goal:** Build a separate gameplay Foci HUD that shows equipped Foci, active/dormant state, Apex/resonance status, and active ability cooldown.

**Architecture:** Add a new client-only `FociHud` layer with its own config/keybind, backed by a server-authoritative `FocusAbilityState` and clientbound payload for the selected ability Focus/cooldown. Keep `CombatHud` focused on target/matchup status, and share sidecar placement so the two HUDs do not overlap.

**Tech Stack:** Fabric API HUD rendering/networking, Minecraft 26.1.2 client item rendering, Java 25 records/interfaces, JUnit source/contract tests, PNG HUD assets.

---

### Task 1: Contracts For HUD Toggle, Layer, And Asset

**Files:**
- Create: `src/test/java/dev/attuned/client/FociHudContractTest.java`
- Modify after red: `src/client/java/dev/attuned/client/AttunedClientConfig.java`
- Modify after red: `src/client/java/dev/attuned/client/AttunedKeybinds.java`
- Modify after red: `src/client/java/dev/attuned/client/AttunedClient.java`
- Create after red: `src/client/java/dev/attuned/client/hud/FociHud.java`
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json`
- Create after red: `src/main/resources/assets/attuned/textures/gui/foci_hud.png`

- [ ] **Step 1: Write the failing test**

Create `FociHudContractTest` asserting:

```java
assertTrue(config.contains("boolean showFociHud"));
assertTrue(config.contains("show_foci_hud"));
assertTrue(keybinds.contains("toggleFociHudKey"));
assertTrue(lang.contains("\"key.attuned.toggle_foci_hud\": \"Toggle Foci HUD\""));
assertTrue(client.contains("FociHud.init()"));
assertTrue(hud.contains("HudElementRegistry.attachElementAfter"));
assertTrue(hud.contains("VanillaHudElements.HOTBAR"));
assertTrue(hud.contains("renderItem"));
assertTrue(hud.contains("Attunement.dormantReasons(player)"));
assertImageSize("gui/foci_hud.png", 88, 154);
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.client.FociHudContractTest --no-daemon
```

Expected: failure because `FociHud` and `foci_hud.png` do not exist and config/keybind text is missing.

- [ ] **Step 3: Implement minimal config/keybind/HUD registration**

Update `AttunedClientConfig` to add `showFociHud`, default `true`, JSON key `show_foci_hud`, setter, and toggle method. Update `AttunedKeybinds` with `toggleFociHudKey` and translation key `key.attuned.toggle_foci_hud`. Update `AttunedClient` to call `FociHud.init()`.

- [ ] **Step 4: Add minimal `FociHud` layer and PNG frame**

Create `FociHud` with HUD registration after hotbar. It should early-return unless `AttunedClientConfig.get().showFociHud()` and the player has any equipped Focus. Draw the frame, render the large ability item stack, render six vertical equipped Focus stacks, dim dormant slots, and draw a readable resonance/Apex bar. Create `foci_hud.png` at `88x154`.

- [ ] **Step 5: Run focused test to green**

Run the same focused test. Expected: pass.

### Task 2: Authoritative Ability Cooldown State

**Files:**
- Create: `src/test/java/dev/attuned/content/AbilityCooldownStateContractTest.java`
- Modify after red: `src/main/java/dev/attuned/api/focus/FocusBehavior.java`
- Create after red: `src/main/java/dev/attuned/network/FocusAbilityStatusPayload.java`
- Create after red: `src/main/java/dev/attuned/network/FocusAbilityState.java`
- Modify after red: `src/main/java/dev/attuned/network/AttunedNetworking.java`
- Modify after red: `src/main/java/dev/attuned/network/JournalNetworking.java`
- Modify after red: `src/main/java/dev/attuned/content/behavior/SmokeBehavior.java`
- Modify after red: `src/main/java/dev/attuned/content/behavior/VoidstepBehavior.java`

- [ ] **Step 1: Write the failing test**

Create a contract test asserting:

```java
assertTrue(behavior.contains("default int abilityCooldownTicks()"));
assertTrue(behavior.contains("default boolean onAbility"));
assertTrue(state.contains("class FocusAbilityState"));
assertTrue(state.contains("firstActiveAbility"));
assertTrue(state.contains("cooldownRemaining"));
assertTrue(state.contains("ServerPlayNetworking.send(player, new FocusAbilityStatusPayload"));
assertTrue(payload.contains("record FocusAbilityStatusPayload(int slot, int remainingTicks, int totalTicks)"));
assertTrue(networking.contains("FocusAbilityState.trigger(player)"));
assertTrue(journal.contains("PayloadTypeRegistry.clientboundPlay().register(FocusAbilityStatusPayload.TYPE"));
assertTrue(smoke.contains("public int abilityCooldownTicks()"));
assertTrue(voidstep.contains("public int abilityCooldownTicks()"));
assertTrue(voidstep.contains("return false"));
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.content.AbilityCooldownStateContractTest --no-daemon
```

Expected: failure because the shared state and payload do not exist, and `onAbility` still returns `void`.

- [ ] **Step 3: Change behavior API**

Change `FocusBehavior#onAbility` to return `boolean`, defaulting `false`. Add `abilityCooldownTicks()` defaulting `0`. Update Smoke and Voidstep to return `true` only when the ability fires successfully.

- [ ] **Step 4: Implement `FocusAbilityState`**

Implement server-side helper that selects the first active ability Focus, checks cooldown maps by player UUID and behavior id/slot, calls behavior, starts cooldown only on success, ticks/syncs status, and cleans up on disconnect/server stop.

- [ ] **Step 5: Register payload**

Add `FocusAbilityStatusPayload` with slot, remaining ticks, total ticks. Register it in common clientbound registration. Add client receiver later in Task 3.

- [ ] **Step 6: Run focused test to green**

Run the focused test. Expected: pass.

### Task 3: Client Cooldown Receiver And HUD Rendering

**Files:**
- Create: `src/test/java/dev/attuned/client/FociHudCooldownContractTest.java`
- Create after red: `src/client/java/dev/attuned/client/FocusAbilityClientState.java`
- Modify after red: `src/client/java/dev/attuned/client/hud/FociHud.java`
- Modify after red: `src/client/java/dev/attuned/client/AttunedClient.java`

- [ ] **Step 1: Write the failing test**

Assert:

```java
assertTrue(clientState.contains("ClientPlayNetworking.registerGlobalReceiver(FocusAbilityStatusPayload.TYPE"));
assertTrue(clientState.contains("remainingTicks"));
assertTrue(clientState.contains("totalTicks"));
assertTrue(hud.contains("FocusAbilityClientState"));
assertTrue(hud.contains("drawCooldownRing"));
assertTrue(hud.contains("remainingTicks()"));
assertTrue(client.contains("FocusAbilityClientState.init()"));
```

- [ ] **Step 2: Run focused test red**

Run `.\gradlew.bat test --tests dev.attuned.client.FociHudCooldownContractTest --no-daemon`.

- [ ] **Step 3: Implement client state**

Create `FocusAbilityClientState` to receive payloads, store slot/remaining/total, and expose progress for `FociHud`.

- [ ] **Step 4: Implement HUD cooldown ring**

In `FociHud`, draw a compact ring around the large active ability Focus when remaining ticks are above zero. Draw no ring if no active ability Focus or cooldown is ready.

- [ ] **Step 5: Run focused test green**

Run the same focused test. Expected: pass.

### Task 4: Placement And Visual Polish

**Files:**
- Modify: `src/client/java/dev/attuned/client/hud/CombatHud.java`
- Modify: `src/client/java/dev/attuned/client/hud/FociHud.java`
- Test: `src/test/java/dev/attuned/client/FociHudContractTest.java`

- [ ] **Step 1: Add test assertions**

Assert `FociHud` uses the primary hotbar sidecar and `CombatHud` contains an alternate sidecar when Foci HUD is visible.

- [ ] **Step 2: Implement placement**

Keep Foci HUD in the primary sidecar position. Update Combat HUD placement helper to avoid that area when Foci HUD is visible.

- [ ] **Step 3: Run focused client HUD tests**

Run:

```powershell
.\gradlew.bat test --tests dev.attuned.client.FociHudContractTest --tests dev.attuned.client.CombatHudSettingsContractTest --no-daemon
```

Expected: pass.

### Task 5: Full Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run focused new tests**

Run:

```powershell
.\gradlew.bat test --tests dev.attuned.client.FociHudContractTest --tests dev.attuned.client.FociHudCooldownContractTest --tests dev.attuned.content.AbilityCooldownStateContractTest --no-daemon
```

- [ ] **Step 2: Run full test suite**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

- [ ] **Step 3: Run build**

Run:

```powershell
.\gradlew.bat build --no-daemon
```

- [ ] **Step 4: Run diff check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors; CRLF warnings are acceptable on this workspace.
