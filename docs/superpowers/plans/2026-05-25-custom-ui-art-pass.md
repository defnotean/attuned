# Custom UI Art Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Attuned's flat UI surfaces with custom pixel-art assets for the altar, focus panel, combat HUD, and journal while preserving gameplay and vanilla-friendly layout.

**Architecture:** Generate deterministic PNG assets for exact Minecraft GUI sizes, then reference them from focused client renderers. Keep dynamic state in Java: text, capacity bars, resonance, hover state, and item slots remain code-driven.

**Tech Stack:** Java 25, Fabric/Loom, JUnit 5, Minecraft GUI textures, PNG assets generated with Pillow.

---

### Task 1: Guard Custom UI Assets

**Files:**
- Create: `src/test/java/dev/attuned/client/UiAssetContractTest.java`
- Modify after red: `src/main/resources/assets/attuned/textures/gui/altar.png`
- Modify after red: `src/main/resources/assets/attuned/textures/gui/focus_panel.png`
- Modify after red: `src/main/resources/assets/attuned/textures/gui/hud_backplate.png`
- Modify after red: `src/main/resources/assets/attuned/textures/item/attunement_journal.png`

- [ ] Write a JUnit test that reads each PNG with `ImageIO`, asserts it exists, and asserts dimensions: altar 216x190, focus panel 28x124, HUD backplate 50x24, journal 16x16.
- [ ] Run `.\gradlew.bat test --tests dev.attuned.client.UiAssetContractTest` and confirm the new missing-asset/dimension assertions fail.
- [ ] Generate the four assets with a deterministic script and re-run the test until it passes.

### Task 2: Render Custom Altar Art

**Files:**
- Modify: `src/client/java/dev/attuned/client/screen/AltarScreen.java`

- [ ] Add an `Identifier` for `textures/gui/altar.png`.
- [ ] Replace the solid panel/inventory well background fills with a single texture blit.
- [ ] Keep dynamic rendering for the shard socket tint, capacity bar, hover ring, and labels.
- [ ] Run `.\gradlew.bat test --tests dev.attuned.client.UiAssetContractTest`.

### Task 3: Render Custom Focus Panel Art

**Files:**
- Modify: `src/client/java/dev/attuned/client/FocusPanel.java`

- [ ] Add an `Identifier` for `textures/gui/focus_panel.png`.
- [ ] Draw the texture behind the six Focus slots.
- [ ] Keep active glow, dormant dim, gem, resonance ring, and capacity bar dynamic.
- [ ] Add small priority ticks on each slot so slot order reads without text.
- [ ] Run `.\gradlew.bat test`.

### Task 4: Render Custom Combat HUD Art

**Files:**
- Modify: `src/client/java/dev/attuned/client/hud/CombatHud.java`

- [ ] Add an `Identifier` for `textures/gui/hud_backplate.png`.
- [ ] Draw the backplate behind the player gem, resonance bar, and optional target gem.
- [ ] Keep the HUD compact above the hotbar and leave the playfield clear.
- [ ] Run `.\gradlew.bat test`.

### Task 5: Improve Journal and Tooltip Copy

**Files:**
- Modify: `src/main/java/dev/attuned/content/AttunementJournalItem.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`

- [ ] Expand the journal to cover quick start, Focus priority, capacity, affinities, Discord, Pacts, Apex, Shards/Altar, The Unseen, and Lootr-friendly loot.
- [ ] Keep pages short enough for the vanilla book UI.
- [ ] Improve tooltip labels so Focus metadata scans cleanly.
- [ ] Run `.\gradlew.bat test`.

### Task 6: Release and Publish

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `README.md`

- [ ] Bump `mod_version` to the next patch release.
- [ ] Update the Modrinth changelog block with the custom UI art pass details.
- [ ] Update README with the UI polish release note.
- [ ] Run `.\gradlew.bat test`, `git diff --check`, and `.\gradlew.bat build`.
- [ ] Commit with a detailed body.
- [ ] Push `main` to GitHub.
- [ ] Publish to Modrinth with `.\gradlew.bat modrinth` if `MODRINTH_TOKEN` is available.
- [ ] Publish to CurseForge through configured browser/API flow, preserving release notes and dependencies.
