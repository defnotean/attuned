# Attuned — Focus Confluences — Implementation Plan

## Goal

Add **Focus Confluences**: small, data-defined set bonuses that wake when a specific 2–3 Focus combination is simultaneously **ACTIVE** (equipped *and* within the attunement budget, not merely carried), granting modest attribute/behavior effects, layered with a discovery/journal collection meta so players uncover Confluences by experimenting with builds. The feature ships **zero** new items, textures, or loot tables — every Confluence references already-registered Foci as members and grants effects through the existing `ModifierEntry` apply/remove machinery. The budget core (capacity, dormancy, resolution) is **untouched**: a Confluence costs no attunement budget and is purely an emergent reward for an already-paid-for build.

## Architecture / Tech Stack

Datapack-driven `SynergyDefinition` records loaded into a synced `DynamicRegistries.registerSynced` registry (parallel to `FocusDefinition`); a **pure, Minecraft-free** `SynergyResolver` (String ids only, no `net.minecraft.*` imports) decides activation and "one-away" previews; a `Synergies` server-tick runtime (mirroring `Pacts`) diffs active Confluences each throttled tick, applies/removes modifiers under stable per-Confluence ids, fires behavior hooks via the existing `AttunedRegistries.getBehavior` lookup, announces transitions, awards advancements, and runs first-discovery fanfare; a persistent + `targetOnly`-synced + `copyOnDeath` `DISCOVERED_CONFLUENCES` attachment feeds the journal; a client `AttunementReadout.Snapshot` field + `FociHud` chip surface activation. **Components over inheritance, pure resolvers behind every policy decision, idempotent inits, cleanup-registered per-player/server maps, and a strict test split** — behavioral tests are Minecraft-free (String-id models copied from `PresetApplicationResolverTest`); everything runtime-flavored is pinned by source-grep contract tests plus the boot smoke check. The feature is **mixin-free** (it hooks only `FocusBehavior` and Fabric server events).

## READ THIS FIRST — load-bearing constraints

1. **The test classpath CANNOT Bootstrap Minecraft.** `new ItemStack(item)` / registry construction / registry-codecs FAIL with "Components not bound yet". Behavioral tests must be Minecraft-free, modelling Foci as `String` ids (copy `PresetApplicationResolverTest`). Everything runtime-flavored uses source-grep contract tests that assert the source file `.contains(...)` literal strings, plus the smoke check.
2. **Before editing ANY existing source file, grep `src/test` for strings it contains.** The suite pins literal source strings; if an edit changes a pinned string, update the pinning test in the **SAME commit**, preserving the assertion intent (never delete it).
3. **Mixin `@At` targets are strings the compiler cannot validate. This feature is mixin-FREE** (`FocusBehavior` hooks + Fabric server events only) — keep it that way. Any mixin change would require `python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60`.
4. **Verify unfamiliar Minecraft API names in this fork** by extracting strings from `~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar` (no `javap`). Forked client mappings: `GuiGraphicsExtractor`/`extractBackground`/`extractLabels`, `keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, boolean)`, `event.modifiers()` returns an `int` GLFW bitmask. **In this fork `ResourceLocation`→`Identifier`** (use `Identifier.fromNamespaceAndPath(...)` + `Identifier.CODEC`); `Registry`/`ResourceKey` are `net.minecraft.core.Registry` / `net.minecraft.resources.ResourceKey`.
5. **Clicks outside a screen's logical `imageWidth` × `imageHeight` window drop the carried item** — only relevant if touching screens; the journal/HUD here are render-only, so this is informational.
6. **Never `git add -A` / `git add .`** — repo root has scratch dirs (`.codex-remote-attachments/`, `.superpowers/`, `assets/`, `tmp/`) and `docs/superpowers/assets/` is Git LFS. Stage explicit paths only.
7. **Run order per task:** write failing test → focused red run (`.\gradlew.bat test --tests dev.attuned.<FQCN> --no-daemon`) → implement → focused green run. **Full gate before release:** `.\gradlew.bat cleanTest build --no-daemon` ; `python tools/verify_repository.py` ; `python -m unittest discover -s tests` ; `python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60` ; `git diff --check`.
8. **Per-player static maps MUST register cleanup in `init()`:** `AttunedPlayerCleanup.onForget(MAP::remove)` (or `onForgetPlayer`) and `AttunedServerCleanup.onStop(MAP::clear)` / `onStopServer(...)` — see `Pacts.init` / `MossheartBehavior.initLifecycle`.
9. **Lang + docs ride along:** every player-visible addition needs `assets/attuned/lang/en_us.json` keys AND a row/sentence in `docs/reference.md`. `AttunedTooltips` auto-appends `item.attuned.<path>.lore`/`.lore2`/`.effect` for every attuned **item** — but Confluences are not items, so they have no `item.attuned.*` keys; their player-visible keys are `confluence.attuned.*` and `journal.attuned.confluence.*`, all of which must exist or the runtime announce/journal shows RAW KEYS in game.
10. **Changelog:** append bullets under a `## Attuned <version>` heading at the TOP of `CHANGELOG.md` as part of the work. The Modrinth task and `tools/publish_curseforge.py` parse it. Do **not** bump `mod_version` until the final release task. **Never attribute Claude in any VCS artifact** (commits, co-authored-by, PR text).
11. **Known hard facts from source (use verbatim):**
    - `AttunedRegistries.FOCUS_DEFINITIONS` is a `ResourceKey<Registry<FocusDefinition>>` (confirmed `AttunedRegistries.java:19-20`).
    - Behaviors are a hand-rolled `Map<Identifier,FocusBehavior>` via `AttunedRegistries.registerBehavior`/`getBehavior` — `getBehavior(Identifier)` is the **single** behavior-id resolution point (`AttunedRegistries.java:25-37`).
    - `AttunedAttachments.MILESTONES` is a `List<String>` persistent + `copyOnDeath` attachment (precedent for a discovery set). `AttunedAttachments` has `sawOnboarding`/`markOnboarding` used by `Pacts.maybeFanfare`.
    - `FocusDefinition`: `Holder<Item> item`, `int cost(0..64)`, `boolean unique`, `Optional<Affinity> affinity`, `Optional<Identifier> faction`, `List<ModifierEntry> modifiers`, `Optional<Identifier> behavior`; `CODEC` uses `optionalFieldOf` (`FocusDefinition.java:21-52`). `ModifierEntry(Holder<Attribute>, double amount, Operation)`.
    - `Attunement.activeSlots(player)` returns **`List<Integer>`** (`Attunement.java:83`); `Attunement.definitionFor(player, stack)` returns `Optional<FocusDefinition>` (`Attunement.java:53`); `Attunement.resolution(player)` is cached server-side, recomputed client-side (`isClientSide()` branch).
    - `AttunedAttachments.getInventory(player)` returns an **`AttunedInv`** (`AttunedAttachments.java:103`), not a raw `List<ItemStack>` — index it with its own slot accessor (`get(slot)`), NOT `List.get`.
    - **`AttunedAdvancements`, `AttunedPlayerCleanup`, `AttunedServerCleanup` all live in the ROOT package `dev.attuned`** (confirmed by `Pacts.java:3-6` imports). Import `dev.attuned.AttunedAdvancements`, `dev.attuned.AttunedPlayerCleanup`, `dev.attuned.AttunedServerCleanup` — there is no `dev.attuned.advancement.*` / `dev.attuned.cleanup.*`.
    - `FocusBehavior.onActivate(ServerPlayer, ItemStack)` / `onDeactivate(ServerPlayer, ItemStack)` are the exact signatures (`FocusBehavior.java:16,19`).
    - `Pacts` is the set-bonus precedent (tick `% N` throttle, transition diffing, announce, advancements, fanfare, `previewOf`, cleanup). Its JOIN handler shape is `ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { ServerPlayer player = handler.player; ... });` (`Pacts.java:145-146`).
    - `Synergies.init()` must be wired **after** `Pacts.init();` and **before** `PactDeathMessages.init();` (`Attuned.java:56-57`).
    - **MEMBER-ID INVARIANT (load-bearing).** A Confluence's `members` are Focus **item** ids — the string `BuiltInRegistries.ITEM.getKey(def.item().value())` yields, i.e. `attuned:lantern_focus` — **NOT** the behavior id `attuned:lantern`. The runtime active-set in Task 2 is built from `definitionFor(...).item()` keys, so members, the `SynergyResolver` String model, the authored `synergy/*.json`, and every test MUST use the `_focus`-suffixed item ids. Any prose table in this plan that shows a bare behavior id (`lantern`, `veil`, …) is shorthand for the design narrative only; the canonical id everywhere in code/data/tests is `attuned:<name>_focus`. A mismatch makes the Confluence silently never fire.

---

### Task 1: SynergyResolver (pure) + SynergyDefinition datapack registry + codec

The foundation slice: a pure Minecraft-free resolver, the datapack `SynergyDefinition` record + codec, and the synced registry. No runtime, no attachment, no UI yet.

**Files**
- Create `src/test/java/dev/attuned/synergy/SynergyResolverTest.java` (behavioral, Minecraft-free — no Bootstrap; package `dev.attuned.synergy` mirrors the resolver).
- Create `src/main/java/dev/attuned/synergy/SynergyResolver.java` (pure static resolver, **no** `net.minecraft.*` imports).
- Create `src/main/java/dev/attuned/api/synergy/SynergyDefinition.java` (record + `CODEC`, parallel to `dev.attuned.api.focus.FocusDefinition`).
- Create `src/test/java/dev/attuned/synergy/SynergyHolderCodecRoundTripTest.java` (NBT round-trip pinning `SynergyDefinition.CODEC`, modelled on `FocusHolderCodecRoundTripTest`).
- Modify `src/main/java/dev/attuned/AttunedRegistries.java` (add `SYNERGY_DEFINITIONS` registry key beside `FOCUS_DEFINITIONS`).
- Modify `src/main/java/dev/attuned/Attuned.java` (register the new dynamic registry in `onInitialize()`, beside line 40).

**Steps**

- [ ] **RED 1 — write the failing resolver behavioral test.** Create `src/test/java/dev/attuned/synergy/SynergyResolverTest.java`. Minecraft-free (no Bootstrap, no `net.minecraft.*` imports — load-bearing constraint #1; mirror `PresetApplicationResolverTest`). Model a synergy def as the resolver's own nested pure type `SynergyResolver.SynergyDef(String id, List<String> members)` — NOT the registry `SynergyDefinition`, which drags in Minecraft. Pin these exact `@Test` methods and assertions:

  ```java
  package dev.attuned.synergy;

  import static org.junit.jupiter.api.Assertions.assertEquals;
  import static org.junit.jupiter.api.Assertions.assertFalse;
  import static org.junit.jupiter.api.Assertions.assertTrue;

  import java.util.List;
  import java.util.Set;
  import org.junit.jupiter.api.Test;

  class SynergyResolverTest {

      private static final SynergyResolver.SynergyDef HUNTERS_PATIENCE =
          new SynergyResolver.SynergyDef("attuned:hunters_patience",
              List.of("attuned:lantern", "attuned:veil"));
      private static final SynergyResolver.SynergyDef CARTOGRAPHERS_TRUST =
          new SynergyResolver.SynergyDef("attuned:cartographers_trust",
              List.of("attuned:beacon", "attuned:waystone", "attuned:driftglass"));

      @Test
      void exactTwoMemberMatchActivatesTheConfluence() {
          Set<String> active = SynergyResolver.activeConfluences(
              Set.of("attuned:lantern", "attuned:veil"), List.of(HUNTERS_PATIENCE));
          assertEquals(Set.of("attuned:hunters_patience"), active,
              "All members active wakes exactly that Confluence.");
      }

      @Test
      void missingOneMemberLeavesTheConfluenceDormant() {
          Set<String> active = SynergyResolver.activeConfluences(
              Set.of("attuned:lantern"), List.of(HUNTERS_PATIENCE));
          assertTrue(active.isEmpty(),
              "A Confluence with a missing member is never active.");
      }

      @Test
      void supersetOfMembersStillActivates() {
          Set<String> active = SynergyResolver.activeConfluences(
              Set.of("attuned:lantern", "attuned:veil", "attuned:anchor"),
              List.of(HUNTERS_PATIENCE));
          assertEquals(Set.of("attuned:hunters_patience"), active,
              "Extra active Foci beyond the members do not block activation.");
      }

      @Test
      void threeMemberConfluenceNeedsAllThree() {
          assertTrue(SynergyResolver.activeConfluences(
                  Set.of("attuned:beacon", "attuned:waystone"),
                  List.of(CARTOGRAPHERS_TRUST)).isEmpty(),
              "Two of three members is not enough for a triad Confluence.");
          assertEquals(Set.of("attuned:cartographers_trust"),
              SynergyResolver.activeConfluences(
                  Set.of("attuned:beacon", "attuned:waystone", "attuned:driftglass"),
                  List.of(CARTOGRAPHERS_TRUST)),
              "All three members active wakes the triad.");
      }

      @Test
      void emptyActiveSetWakesNothing() {
          assertTrue(SynergyResolver.activeConfluences(Set.of(), List.of(HUNTERS_PATIENCE)).isEmpty(),
              "No active Foci means no Confluences.");
      }

      @Test
      void emptyDefinitionTableWakesNothing() {
          assertTrue(SynergyResolver.activeConfluences(
                  Set.of("attuned:lantern", "attuned:veil"), List.of()).isEmpty(),
              "No defined Confluences means an empty result regardless of active Foci.");
      }

      @Test
      void overlappingConfluencesSharingAMemberBothWakeTogether() {
          SynergyResolver.SynergyDef sharedA =
              new SynergyResolver.SynergyDef("attuned:a", List.of("attuned:lantern", "attuned:veil"));
          SynergyResolver.SynergyDef sharedB =
              new SynergyResolver.SynergyDef("attuned:b", List.of("attuned:lantern", "attuned:smoke"));
          Set<String> active = SynergyResolver.activeConfluences(
              Set.of("attuned:lantern", "attuned:veil", "attuned:smoke"),
              List.of(sharedA, sharedB));
          assertEquals(Set.of("attuned:a", "attuned:b"), active,
              "Two Confluences sharing the lantern member both wake when their full member sets are active.");
      }

      @Test
      void emptyMembersListNeverActivatesEvenWhenFociAreActive() {
          SynergyResolver.SynergyDef degenerate =
              new SynergyResolver.SynergyDef("attuned:degenerate", List.of());
          assertFalse(
              SynergyResolver.activeConfluences(Set.of("attuned:lantern"), List.of(degenerate))
                  .contains("attuned:degenerate"),
              "A memberless Confluence is treated as inert, not vacuously active.");
      }
  }
  ```

  > NOTE on the last case: `Set.containsAll(emptySet)` is vacuously `true` in plain Java, which would wrongly wake a memberless def. This test pins the guard so `SynergyResolver.activeConfluences` must skip defs with empty members. Implement the skip in GREEN 1 (do not let it pass by accident).

- [ ] **RED 1 run — confirm compile-error red.** `SynergyResolver` does not exist yet, so a compile failure is the acceptable first red:
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyResolverTest" --no-daemon
  ```
  Expected: `cannot find symbol: class SynergyResolver` / `SynergyDef`.

- [ ] **GREEN 1 — implement `SynergyResolver`.** Create `src/main/java/dev/attuned/synergy/SynergyResolver.java`. Pure, Minecraft-free (compiles onto the unit-test classpath):

  ```java
  package dev.attuned.synergy;

  import java.util.LinkedHashSet;
  import java.util.List;
  import java.util.Set;

  /**
   * Pure, Minecraft-free policy for Focus Confluences: a Confluence is active iff
   * every one of its member Focus ids is in the active set. Modelled on
   * {@code BudgetResolver}/{@code PresetApplicationResolver} (String ids only) so its
   * test classpath never needs to Bootstrap Minecraft.
   */
  public final class SynergyResolver {
      private SynergyResolver() {}

      /** A Confluence's identity and its required member Focus ids (the testable shape). */
      public record SynergyDef(String id, List<String> members) {
          public SynergyDef {
              members = List.copyOf(members);
          }
      }

      /**
       * @param activeFocusIds ids of currently-active Foci (e.g. "attuned:lantern_focus")
       * @param defs the loaded Confluence table
       * @return ids of every Confluence whose full member set is active
       */
      public static Set<String> activeConfluences(Set<String> activeFocusIds, List<SynergyDef> defs) {
          Set<String> active = new LinkedHashSet<>();
          for (SynergyDef def : defs) {
              if (def.members().isEmpty()) {
                  continue; // a memberless Confluence is inert, not vacuously active
              }
              if (activeFocusIds.containsAll(def.members())) {
                  active.add(def.id());
              }
          }
          return active;
      }
  }
  ```

- [ ] **GREEN 1 run — confirm the resolver test passes.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyResolverTest" --no-daemon
  ```
  Expected: all 8 tests pass.

- [ ] **RED 2 — write the failing codec round-trip test.** Create `src/test/java/dev/attuned/synergy/SynergyHolderCodecRoundTripTest.java`. This DOES touch Minecraft codec/NBT types (like `FocusHolderCodecRoundTripTest`) but stays Bootstrap-free by exercising only the empty-`modifiers` / absent-`behavior` path, where no item/attribute registry binding is needed (`Identifier.CODEC` is a plain string codec — `FocusDefinition.java:49` uses it for `faction` with no Bootstrap). Pin exactly:

  ```java
  package dev.attuned.synergy;

  import static org.junit.jupiter.api.Assertions.assertEquals;
  import static org.junit.jupiter.api.Assertions.assertTrue;

  import com.mojang.serialization.Codec;
  import dev.attuned.api.synergy.SynergyDefinition;
  import java.util.List;
  import java.util.Optional;
  import net.minecraft.nbt.NbtOps;
  import net.minecraft.nbt.Tag;
  import net.minecraft.resources.Identifier;
  import org.junit.jupiter.api.Test;

  /**
   * Round-trips a {@link SynergyDefinition} through its REAL codec (encode to NBT,
   * then decode), pinning the codec wiring the way {@code FocusHolderCodecRoundTripTest}
   * pins {@code FocusHolder}'s. Empty modifiers + absent behavior keep this Minecraft-free
   * (no item/attribute registry / Bootstrap needed).
   */
  class SynergyHolderCodecRoundTripTest {

      @Test
      void membersOnlyDefinitionRoundTripsPreservingMembersEmptyModifiersAndNoBehavior() {
          Codec<SynergyDefinition> codec = SynergyDefinition.CODEC;
          SynergyDefinition original = new SynergyDefinition(
              List.of(
                  Identifier.fromNamespaceAndPath("attuned", "lantern"),
                  Identifier.fromNamespaceAndPath("attuned", "veil")),
              List.of(),
              Optional.empty());

          Tag encoded = codec.encodeStart(NbtOps.INSTANCE, original)
              .result()
              .orElseThrow(() -> new AssertionError("SynergyDefinition failed to encode through its codec"));
          SynergyDefinition decoded = codec.parse(NbtOps.INSTANCE, encoded)
              .result()
              .orElseThrow(() -> new AssertionError("SynergyDefinition failed to decode through its codec"));

          assertEquals(2, decoded.members().size(),
              "Both member ids survive the encode/decode round-trip.");
          assertEquals(original.members(), decoded.members(),
              "Member id order and values are preserved through the codec.");
          assertTrue(decoded.modifiers().isEmpty(),
              "An omitted modifiers field decodes to the empty default list.");
          assertTrue(decoded.behavior().isEmpty(),
              "An absent behavior decodes to Optional.empty().");
      }
  }
  ```

- [ ] **RED 2 run — confirm compile-error red.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyHolderCodecRoundTripTest" --no-daemon
  ```
  Expected: `package dev.attuned.api.synergy does not exist` / `cannot find symbol: class SynergyDefinition`.

- [ ] **GREEN 2 — implement `SynergyDefinition` record + codec.** Create `src/main/java/dev/attuned/api/synergy/SynergyDefinition.java`, mirroring `FocusDefinition.CODEC` (`members` required; `modifiers` `optionalFieldOf` default `List.of()`; `behavior` `optionalFieldOf`). Reuse the real `ModifierEntry.CODEC` from `dev.attuned.api.focus`:

  ```java
  package dev.attuned.api.synergy;

  import com.mojang.serialization.Codec;
  import com.mojang.serialization.codecs.RecordCodecBuilder;
  import dev.attuned.api.focus.ModifierEntry;
  import net.minecraft.resources.Identifier;

  import java.util.List;
  import java.util.Objects;
  import java.util.Optional;

  /**
   * Data-driven definition of a Confluence — the member Foci that must all be active,
   * the modifiers granted while active, and an optional code behaviour.
   * Loaded from datapack JSON at {@code data/<namespace>/attuned/synergy/<name>.json}.
   * A Confluence costs no attunement budget; it is an emergent reward for an
   * already-paid-for build (see the Focus Confluences design).
   */
  public record SynergyDefinition(
          List<Identifier> members,
          List<ModifierEntry> modifiers,
          Optional<Identifier> behavior) {

      public SynergyDefinition {
          members = List.copyOf(Objects.requireNonNull(members, "members"));
          modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
          behavior = Objects.requireNonNull(behavior, "behavior");
      }

      public static final Codec<SynergyDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
          Identifier.CODEC.listOf().fieldOf("members").forGetter(SynergyDefinition::members),
          ModifierEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(SynergyDefinition::modifiers),
          Identifier.CODEC.optionalFieldOf("behavior").forGetter(SynergyDefinition::behavior)
      ).apply(instance, SynergyDefinition::new));
  }
  ```

  > NOTE: `members` is `List<Identifier>` here (the datapack/registry type), distinct from the resolver's `List<String>`. Task 2 bridges them via `Identifier#toString()` when building the active-id set for `SynergyResolver.activeConfluences`.

- [ ] **GREEN 2 run — confirm the codec test passes.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyHolderCodecRoundTripTest" --no-daemon
  ```
  Expected: 1 test passes. If it fails with a registry-Bootstrap error, the `Identifier.CODEC` assumption is wrong — re-check, do not silently swap to `Codec.STRING`.

- [ ] **Wire the registry — `AttunedRegistries` + `Attuned`.** (No standalone unit test gates the registry-key declaration; the source-grep `SynergyDefinitionContractTest` in Task 4 covers it.) Edit `src/main/java/dev/attuned/AttunedRegistries.java`:

  Add the import beside the existing `FocusDefinition` import (line 4):
  ```java
  import dev.attuned.api.synergy.SynergyDefinition;
  ```
  Add the registry key immediately after the `FOCUS_DEFINITIONS` block (after line 20), using the exact idiom with registry name `"synergy"` (so JSONs load from `data/<ns>/attuned/synergy/<id>.json`):
  ```java
  /** Datapack registry of Confluence definitions ({@code data/<ns>/attuned/synergy/<name>.json}). */
  public static final ResourceKey<Registry<SynergyDefinition>> SYNERGY_DEFINITIONS =
      ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "synergy"));
  ```

  Then edit `src/main/java/dev/attuned/Attuned.java` — add the import beside line 3:
  ```java
  import dev.attuned.api.synergy.SynergyDefinition;
  ```
  and register the synced dynamic registry immediately after the `FOCUS_DEFINITIONS` registration (after line 40) — the registry MUST be registered in `onInitialize()` before any code accesses it:
  ```java
  DynamicRegistries.registerSynced(AttunedRegistries.SYNERGY_DEFINITIONS, SynergyDefinition.CODEC);
  ```

  > NOTE: `Synergies.init()` (the runtime tick loop) is intentionally NOT added here — it belongs to Task 2 and is wired after `Pacts.init()` at line 56.

- [ ] **GREEN — focused run over the Task 1 surface.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyResolverTest" --tests "dev.attuned.synergy.SynergyHolderCodecRoundTripTest" --no-daemon
  ```
  Expected: 9 tests pass (8 resolver + 1 codec). This also confirms `AttunedRegistries` and `SynergyDefinition` compile cleanly against `src/main`.

- [ ] **Full unit-test gate — no regression.** The registry edit touches shared production classes (`AttunedRegistries`, `Attuned`):
  ```
  .\gradlew.bat test --no-daemon
  ```
  Expected: green, with the two new classes included and no existing focus/codec/bootstrap contract test broken by the new imports. Note `BootstrapRegistrationContractTest` pins `Attuned.onInitialize` content — confirm it does not assert a complete/ordered registration list that the new `registerSynced` line breaks; if it does, update its pin in this same commit, preserving intent.

- [ ] **Smoke gate — `runClient` boot check.** The new `registerSynced` is a live mod-init call; a malformed registry key or codec only surfaces at datapack-load. Boot far enough to load datapacks once:
  ```
  .\gradlew.bat runClient --no-daemon
  ```
  Expected: `"Attuned initializing"` (from `Attuned.java:69`) with no `IllegalStateException`/registry-freeze/codec error referencing `synergy`. With zero `synergy/*.json` shipped here, the registry loads empty — correct for this task. (Shipping the first authored Confluences happens in Task 5, gated by `SynergyDefinitionContractTest` and the `docs/reference.md` row.)

**Files touched by Task 1 (all absolute):**
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\synergy\SynergyResolverTest.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\synergy\SynergyResolver.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\api\synergy\SynergyDefinition.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\synergy\SynergyHolderCodecRoundTripTest.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\AttunedRegistries.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\Attuned.java` (modify)

---

### Task 2: Synergies runtime (detection, modifier apply/remove, transitions, fanfare, advancements, cleanup)

Depends on **Task 1** (`SynergyResolver.activeConfluences(Set<String>, List<SynergyDef>)`, the `SynergyDefinition` record + `CODEC`, and the `AttunedRegistries.SYNERGY_DEFINITIONS` registry registered before `Pacts.init()`). This wires the server-tick runtime that turns the resolver verdict into applied modifiers, behavior hooks, chat transitions, advancements, and first-discovery fanfare, with full per-player/server cleanup. The discovery **attachment + journal** land in Task 3; here we consume the existing `sawOnboarding`/`markOnboarding` onboarding marker for the fanfare gate and write the `confluence_<id>` advancement.

> **Bridge decision (pin this and keep it consistent across Tasks 2/3/5):** the runtime builds the resolver's `List<SynergyDef>` from the registry by reading each `SynergyDefinition`'s `members()` (a `List<Identifier>`) into `List<String>` via `Identifier#toString()`, and keys each `SynergyDef.id()` by the **registry key string** of that definition (`registry.getKey(def).toString()`). The active-Focus id set is built the same way: `BuiltInRegistries.ITEM.getKey(def.item().value()).toString()`. So a member entry like `"attuned:lantern_focus"` matches an active Focus id `"attuned:lantern_focus"` — author the datapack `"members"` as the **item ids**, not bare names.

**Files**
- Create `src/main/java/dev/attuned/synergy/Synergies.java`
- Create `src/test/java/dev/attuned/synergy/SynergiesRuntimeContractTest.java`
- Modify `src/main/java/dev/attuned/Attuned.java` (insert `Synergies.init();` immediately after `Pacts.init();`, before `PactDeathMessages.init();`; add `import dev.attuned.synergy.Synergies;`)
- Modify `src/main/resources/assets/attuned/lang/en_us.json` (transition + fanfare lang keys, one `.name`/`.desc` pair per shipped Confluence)
- Create `src/main/resources/data/attuned/advancement/attunement/confluence_<id>.json` (one per shipped Confluence, child of `attunement/root`)

> **VERIFY (fork API names — confirm before relying on the literal strings).** All tokens below are copied verbatim from existing in-tree call sites, so they are low-risk; the VERIFY note exists only so a mapping drift surfaces at **compile** (Task step "GREEN — focused run"), not runtime. Extraction command if a symbol fails to compile:
> ```
> $jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\fabric-loom\26.1.2\minecraft-client.jar"
> python -c "import zipfile,re,sys; z=zipfile.ZipFile(sys.argv[1]); print([s.decode() for n in z.namelist() if n.endswith('.class') for s in re.findall(rb'[ -~]{5,}', z.read(n)) if b'addTransientModifier' in s or b'removeModifier' in s])" "$($jar.FullName)"
> ```
> Confirmed call-site tokens (cited inline): `ServerTickEvents.END_SERVER_TICK.register(...)` (Pacts.java:142-area), `server.getPlayerList().getPlayers()` (Pacts loop), `player.level().registryAccess().lookupOrThrow(...)` (Attunement.java:57-58), `BuiltInRegistries.ITEM.getKey(...)` (CombatContext.java:138), `player.getAttribute(holder)` + `ai.addTransientModifier(...)` / `ai.removeModifier(id)` (AttunedEffects.java:266-287), `player.sendSystemMessage(Component.translatable(...))` (Pacts.java:652), `AttunedAdvancements.award(player, path)` (AttunedAdvancements.java:18), `AttunedAttachments.sawOnboarding/markOnboarding` (AttunedAttachments.java:217-237), `AttunedRegistries.getBehavior(id)` (AttunedRegistries.java:35), and the JOIN handler shape `(handler, sender, server) -> { ServerPlayer player = handler.player; ... }` (Pacts.java:145-146).

**Steps**

- [ ] **RED — write `SynergiesRuntimeContractTest` (source-grep, Minecraft-free) with exact pinned assertions.** Create `src/test/java/dev/attuned/synergy/SynergiesRuntimeContractTest.java`. Read `Synergies.java`, `Attuned.java`, and `en_us.json` once each via `Files.readString(path, StandardCharsets.UTF_8)`. Copy the `methodBody(String source, String signaturePrefix)` and `assertBefore(String source, String earlier, String later)` helpers verbatim from `FocusDataConsistencyTest.java` (or `DirectCombatFocusContractTest.java` — both expose identical helpers; `methodBody` extracts the brace-matched body, `assertBefore` checks first-occurrence ordering). Pin these assertions (each with a message):
  - Wiring order: `assertBefore(attuned, "Pacts.init();", "Synergies.init();")` and `assertBefore(attuned, "Synergies.init();", "PactDeathMessages.init();")` — message `"Synergies.init() must be wired after Pacts.init() and before PactDeathMessages.init()"`.
  - Idempotent guard: `assertTrue(synergies.contains("if (initialized) {"), ...)` and `assertTrue(synergies.contains("initialized = true;"), ...)`.
  - Tick registration + throttle: `assertTrue(synergies.contains("ServerTickEvents.END_SERVER_TICK.register(Synergies::tick)"), ...)` and `assertTrue(synergies.contains("ticks % 20 == 0"), ...)`.
  - Active-id-set build — assert the body of `tickPlayer`: `methodBody(synergies, "private static void tickPlayer(")` `.contains("Attunement.activeSlots(player)")`, `.contains("Attunement.definitionFor(player,")`, `.contains("BuiltInRegistries.ITEM.getKey(")`, `.contains(".value()).toString()")` (the `.toString()` bridge is load-bearing — pin it), and `.contains("SynergyResolver.activeConfluences(")`.
  - Last-state map + diff: `assertTrue(synergies.contains("Map<UUID, Set<String>> synergyState"), ...)` and `assertTrue(synergies.contains("new HashMap<>()"), ...)`.
  - Stable modifier id discipline: `methodBody(synergies, "private static Identifier modifierId(")` `.contains("Identifier.fromNamespaceAndPath(Attuned.MOD_ID, \"confluence_\"")`.
  - Apply/remove symmetry: `assertTrue(synergies.contains("addTransientModifier("), ...)` and `assertTrue(synergies.contains("removeModifier(modifierId("), ...)`.
  - Behavior dispatch (pin the exact `FocusBehavior` method names): `assertTrue(synergies.contains("AttunedRegistries.getBehavior("), ...)`, `assertTrue(synergies.contains("behavior.onActivate("), ...)`, `assertTrue(synergies.contains("behavior.onDeactivate("), ...)`.
  - On-join reconcile (strip-then-reapply): `assertTrue(synergies.contains("ServerPlayConnectionEvents.JOIN.register"), ...)`, `assertTrue(synergies.contains("(handler, sender, server) ->"), "JOIN callback should follow the 3-param arity pattern")`, `assertTrue(synergies.contains("handler.player"), ...)`, and `methodBody(synergies, "private static void reconcileOnJoin(")` `.contains("removeModifier(")`.
  - Transition announce: `assertTrue(synergies.contains("confluence.attuned.gained"), ...)` and `assertTrue(synergies.contains("confluence.attuned.faded"), ...)`.
  - Advancement: `assertTrue(synergies.contains("AttunedAdvancements.award(player, \"attunement/confluence_\""), ...)`.
  - Fanfare gate: `assertTrue(synergies.contains("\"confluence_first_\""), ...)`, `assertTrue(synergies.contains("AttunedAttachments.sawOnboarding(player,"), ...)`, `assertBefore(synergies, "sawOnboarding(player", "markOnboarding(player")`.
  - Cleanup registrations: `assertTrue(synergies.contains("AttunedPlayerCleanup.onForgetPlayer("), ...)`, `assertTrue(synergies.contains("AttunedServerCleanup.onStopServer("), ...)`, `assertTrue(synergies.contains("synergyState.clear();"), ...)`, `assertTrue(synergies.contains("ticks = 0;"), ...)`.
  - Lang presence (generic keys only — per-Confluence `.name`/`.desc` are asserted by the Task 5 content sweep): `assertTrue(lang.contains("\"confluence.attuned.gained\""), ...)`, `.contains("\"confluence.attuned.faded\"")`, `.contains("\"confluence.attuned.first_discovery\"")`.

- [ ] **RED run — expect missing-source / missing-string failure.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergiesRuntimeContractTest" --no-daemon
  ```
  Expect FAIL: `Synergies.java` does not exist, so `Files.readString` throws `NoSuchFileException` (or the helper fires "Missing method signature"). Confirm the failure is the missing source / missing pinned strings, not a compile error in the test file itself.

- [ ] **GREEN — create `Synergies.java` skeleton: idempotent init, fields, tick + JOIN registration.** Create `src/main/java/dev/attuned/synergy/Synergies.java` in package `dev.attuned.synergy`, mirroring `Pacts`:
  - Fields: `private static boolean initialized = false;`, `private static int ticks = 0;`, `private static final Map<UUID, Set<String>> synergyState = new HashMap<>();`.
  - `public static void init()` with `if (initialized) { return; } initialized = true;`. Inside, register: `ServerTickEvents.END_SERVER_TICK.register(Synergies::tick);`, `ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reconcileOnJoin(handler.player));`, plus the cleanup callbacks (later step).
  - Imports: `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents`, `net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents`, `net.minecraft.core.Registry`, `net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.network.chat.Component`, `net.minecraft.resources.Identifier`, `net.minecraft.server.MinecraftServer`, `net.minecraft.server.level.ServerPlayer`, `net.minecraft.world.entity.ai.attributes.AttributeInstance`, `net.minecraft.world.entity.ai.attributes.AttributeModifier`, `net.minecraft.world.item.ItemStack`, plus `dev.attuned.Attuned`, `dev.attuned.AttunedRegistries`, `dev.attuned.AttunedAdvancements`, `dev.attuned.AttunedPlayerCleanup`, `dev.attuned.AttunedServerCleanup`, `dev.attuned.attunement.Attunement`, `dev.attuned.attunement.AttunedAttachments`, `dev.attuned.api.focus.FocusBehavior`, `dev.attuned.api.focus.ModifierEntry`, `dev.attuned.api.synergy.SynergyDefinition`, and `java.util.{ArrayList, HashMap, HashSet, List, Map, Set, UUID}`.
    > NOTE: `AttunedAdvancements`/`AttunedPlayerCleanup`/`AttunedServerCleanup` are in the ROOT `dev.attuned` package (constraint #11) — do NOT write `dev.attuned.advancement.*` or `dev.attuned.cleanup.*`.

- [ ] **GREEN — implement `tick` + `tickPlayer`: throttle, build active-id set, resolve, diff.**
  - `private static void tick(MinecraftServer server)`: `ticks++; if (ticks % 20 != 0) { return; } for (ServerPlayer player : server.getPlayerList().getPlayers()) { tickPlayer(player); }` (Pacts loop, gated wholesale by `% 20` since there is no per-tick work).
  - `private static void tickPlayer(ServerPlayer player)`:
    1. Build the active id set:
       ```java
       Set<String> activeFocusIds = new HashSet<>();
       for (int slot : Attunement.activeSlots(player)) {
           ItemStack stack = AttunedAttachments.getInventory(player).get(slot); // AttunedInv#get, NOT List.get
           Attunement.definitionFor(player, stack).ifPresent(def ->
               activeFocusIds.add(BuiltInRegistries.ITEM.getKey(def.item().value()).toString()));
       }
       ```
       (`Attunement.activeSlots` returns `List<Integer>`; `getInventory` returns `AttunedInv` — index via its own `get(slot)`. `def.item()` is `Holder<Item>`, so `.value()` yields the `Item`. The `.toString()` on the `Identifier` is critical — the resolver compares Strings.)
    2. Load the registry and build the resolver table:
       ```java
       Registry<SynergyDefinition> registry =
           player.level().registryAccess().lookupOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
       List<SynergyResolver.SynergyDef> defs = new ArrayList<>();
       registry.listElements().forEach(holder -> {
           SynergyDefinition def = holder.value();
           String id = registry.getKey(def).toString();
           List<String> members = def.members().stream().map(Identifier::toString).toList();
           defs.add(new SynergyResolver.SynergyDef(id, members));
       });
       ```
    3. `Set<String> now = SynergyResolver.activeConfluences(activeFocusIds, defs);`
    4. `Set<String> was = synergyState.getOrDefault(player.getUUID(), Set.of());`
    5. Diff: for each id in `now` not in `was` → `onGain(player, id, registry);` for each id in `was` not in `now` → `onLoss(player, id, registry);`
    6. `synergyState.put(player.getUUID(), Set.copyOf(now));` **after** the diff (put-after-detect, Pacts gotcha).

- [ ] **GREEN — implement modifier apply/remove with a stable per-Confluence id.**
  - `private static Identifier modifierId(String confluenceId, int index)` returning `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "confluence_" + confluenceId.replace(':', '_') + "_mod_" + index)` — stable per confluence id + modifier index, distinct from the slot-based Focus scheme. Keep the bare `"confluence_"` namespace-path prefix the contract test greps for.
  - `private static void applyModifiers(ServerPlayer player, String confluenceId, SynergyDefinition def)`: loop `def.modifiers()`, `AttributeInstance ai = player.getAttribute(entry.attribute());` with `if (ai == null) continue;`, `Identifier id = modifierId(confluenceId, i); if (ai.getModifier(id) == null) { ai.addTransientModifier(new AttributeModifier(id, entry.amount(), entry.operation())); }` (copy `AttunedEffects.applyFocus`, substituting the id scheme).
  - `private static void removeModifiers(ServerPlayer player, String confluenceId, SynergyDefinition def)`: same loop, `ai.removeModifier(modifierId(confluenceId, i));` (`removeModifier` is null/absent-safe, so it doubles as the on-join strip).
  - To resolve a `SynergyDefinition` from a confluence id string inside `onGain`/`onLoss`: `SynergyDefinition def = registry.get(Identifier.parse(confluenceId));` (the id keys are the registry keys, so this is a direct lookup; guard `if (def == null) return;`).

- [ ] **GREEN — implement `onGain`/`onLoss`: behavior hooks, announce, advancement, fanfare.**
  - `private static void onGain(ServerPlayer player, String confluenceId, Registry<SynergyDefinition> registry)`, in order matching `AttunedEffects.applyFocus`:
    1. `SynergyDefinition def = registry.get(Identifier.parse(confluenceId)); if (def == null) return;`
    2. `applyModifiers(player, confluenceId, def);`
    3. Behavior — `def.behavior().ifPresent(behaviorId -> { FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId); if (behavior != null) { behavior.onActivate(player, ItemStack.EMPTY); } });` — a Confluence has no backing stack; pass `ItemStack.EMPTY`. Behaviors should handle an empty stack gracefully (same as on-join situations). `FocusBehavior.onActivate(ServerPlayer, ItemStack)` is the confirmed signature.
    4. Announce — `player.sendSystemMessage(Component.translatable("confluence.attuned.gained", Component.translatable("confluence.attuned." + pathOf(confluenceId) + ".name")));` where `pathOf` strips the namespace (`Identifier.parse(confluenceId).getPath()`), so the lang key is `confluence.attuned.<name>.name`.
    5. `AttunedAdvancements.award(player, "attunement/confluence_" + pathOf(confluenceId));` (auto-awards root).
    6. `maybeFanfare(player, confluenceId);`
  - `private static void onLoss(ServerPlayer player, String confluenceId, Registry<SynergyDefinition> registry)`: resolve def; `removeModifiers(...)`; behavior `onDeactivate(player, ItemStack.EMPTY)` (null-guarded); announce `Component.translatable("confluence.attuned.faded", <name>)`.
  - `private static void maybeFanfare(ServerPlayer player, String confluenceId)`: copy `Pacts.maybeFanfare` verbatim with `String onboardId = "confluence_first_" + pathOf(confluenceId);` then `if (AttunedAttachments.sawOnboarding(player, onboardId)) { return; } AttunedAttachments.markOnboarding(player, onboardId); fanfare(player, confluenceId);`. `fanfare` plays a celebratory sound (e.g. `UI_TOAST_CHALLENGE_COMPLETE`) and sends `Component.translatable("confluence.attuned.first_discovery", <name>)`. **Keep `fanfare` to sound + message only — the advancement is already awarded in `onGain`** (no double-award). The discovery attachment write happens in Task 3; here the fanfare is purely the celebration.

- [ ] **GREEN — implement `reconcileOnJoin` (strip-then-reapply).** `private static void reconcileOnJoin(ServerPlayer player)`: strip every possible stale confluence modifier, then drop the cached state so the next tick reapplies cleanly:
  ```java
  Registry<SynergyDefinition> registry =
      player.level().registryAccess().lookupOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
  registry.listElements().forEach(holder ->
      removeModifiers(player, registry.getKey(holder.value()).toString(), holder.value()));
  synergyState.remove(player.getUUID());
  ```
  Do **not** reapply inline — clearing the last-state map makes the immediate next `tickPlayer` treat every currently-active Confluence as a fresh gain (re-applying modifiers, re-firing `onActivate`). This guarantees deterministic on-join state with no stale-NBT accumulation. **Ordering is safe:** Fabric's `ServerPlayConnectionEvents.JOIN` runs on the server thread before the player is ticked, so the strip-then-clear completes before the first `tickPlayer` reapplies.

- [ ] **GREEN — register cleanup callbacks inside `init()`.** Mirroring Pacts:
  ```java
  AttunedPlayerCleanup.onForgetPlayer(player -> synergyState.remove(player.getUUID()));
  AttunedServerCleanup.onStopServer(server -> {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
          reconcileOnJoin(player); // strips confluence modifiers from online players for parity with Pacts
      }
      ticks = 0;
      synergyState.clear();
  });
  ```

- [ ] **GREEN — wire `Synergies.init()` in `Attuned.onInitialize`.** Edit `src/main/java/dev/attuned/Attuned.java`: insert `Synergies.init();` on its own line immediately after `Pacts.init();` and before `PactDeathMessages.init();` (line 56-57). Add `import dev.attuned.synergy.Synergies;`. This satisfies `assertBefore(attuned, "Pacts.init();", "Synergies.init();")`.

- [ ] **GREEN — focused run, expect green.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergiesRuntimeContractTest" --no-daemon
  ```
  Expect PASS. If `compileJava`/`compileTestJava` fails on a VERIFY token (e.g. `getAttribute`, `addTransientModifier`, JOIN arity, `FocusBehavior.onActivate` signature), run the jar-extraction command in the VERIFY block, correct the single symbol, and update the matching pinned string in the contract test so source-grep and compile agree. Re-run until green.

- [ ] **GREEN — add lang keys.** Edit `src/main/resources/assets/attuned/lang/en_us.json` (flat object, append before the closing brace). Add the transition/fanfare keys the contract test pins:
  ```json
  "confluence.attuned.gained": "%s flows together.",
  "confluence.attuned.faded": "%s comes apart.",
  "confluence.attuned.first_discovery": "Confluence discovered: %s",
  ```
  plus one `"confluence.attuned.<name>.name"` / `"confluence.attuned.<name>.desc"` pair per Confluence shipped in Task 5 (match the ids Task 5 actually ships, e.g. `hunters_patience`, `cartographers_trust`). If Task 2 lands before Task 5 ships content, add placeholder pairs for the planned ids so the runtime announce never shows a raw key; Task 5's content sweep will pin them to the actual shipped set.

- [ ] **GREEN — add per-Confluence advancement JSONs.** For each shipped Confluence `<name>`, create `src/main/resources/data/attuned/advancement/attunement/confluence_<name>.json` as a child of `attunement/root` (`"parent": "attuned:attunement/root"`, a `display` block using a member Focus item as the icon, a single `"criteria"` entry, and `"requirements"`). **VERIFY the trigger type by reading a sample pact advancement first** — confirm `src/main/resources/data/attuned/advancement/attunement/pact_pyresworn.json` uses `"trigger": "minecraft:impossible"` (code-granted via `AttunedAdvancements.award`, not auto-earned) and copy that exact shape. `AttunedAdvancements.award(player, "attunement/confluence_<name>")` resolves `Identifier.fromNamespaceAndPath("attuned", "attunement/confluence_<name>")` → this file.

- [ ] **SMOKE GATE — server boot loads the class + datapack.** The source-grep test cannot prove the class actually initializes inside a real Fabric mod-init chain or that the advancement/lang JSONs parse. Boot headless:
  ```
  .\gradlew.bat runServer --no-daemon
  ```
  in `run_in_background`, then Monitor the log until `Done (` appears (full init reached) OR an exception / `Failed to load` / `Couldn't load` line appears, then stop it. Pass criteria: `Done (` present with no `Exception`/`Failed to parse`/`Couldn't load` line referencing `attuned:synergy`, `confluence_`, or `Synergies`. If EULA is required, set `eula=true` in `run/eula.txt` first.

- [ ] **GREEN — unit + repository gate.**
  ```
  .\gradlew.bat test --no-daemon
  python tools/verify_repository.py
  ```
  Expect PASS. The new advancement JSONs and lang keys do not add `focus/*.json` files, so the `README_FOCI_PATTERN` count gate is unaffected; the JSON-parseable and no-TODO gates must stay green. If `verify_repository.py` flags a missing `docs/reference.md` Confluences row, that belongs to Task 5 — for Task 2, ensure only that nothing it added breaks the gate.

**Files touched by Task 2 (all absolute):**
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\synergy\Synergies.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\synergy\SynergiesRuntimeContractTest.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\Attuned.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\resources\assets\attuned\lang\en_us.json` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\resources\data\attuned\advancement\attunement\confluence_<name>.json` (new, one per Confluence)

---

### Task 3: Discovery attachment + Journal "Confluences" chapter

Depends on **Task 1** (the `SYNERGY_DEFINITIONS` registry, available client-side via `Minecraft.getInstance().level.registryAccess()`) and **Task 2** (server-side writes). This adds the persistent `DISCOVERED_CONFLUENCES` attachment and a new "Confluences" Journal chapter that renders each known Confluence as a full name+members+effect row when discovered, or a redacted `???` row showing only the member count when not. It reuses the `PRESETS` attachment builder shape, the journal chapter registry + fail-fast drift guard, and the `targetOnly` client-sync read path.

> **VERIFY note up front (do once, before step 1):** the journal screen's `drawPage`/`pageText` path is currently 100% static (no player/attachment reads). This is the first chapter whose rows depend on synced player state, so the Confluences page render must reach `Minecraft.getInstance().player` and call `AttunedAttachments.getDiscoveredConfluences(player)`. Confirm the synced client accessor pattern matches `SatchelScreen.java:259` / `AttunedTooltips.java:97-100` (server getter works on client with synced data — no client-only method).

> **Wiring connect-the-dots:** Task 2's `maybeFanfare` must also call `AttunedAttachments.markConfluenceDiscovered(player, confluenceId)` so the journal flips from redacted to discovered. Add that call in this task (edit `Synergies.fanfare`) and pin it: `assertTrue(synergies.contains("AttunedAttachments.markConfluenceDiscovered("), "First-discovery fanfare must record the discovery for the journal")` inside the Task 2 contract test (update in the SAME commit, preserving its other assertions). Place the `markConfluenceDiscovered` call inside `fanfare` (the one-time path) so it only writes on first discovery.

**Files**
- Modify `src/main/java/dev/attuned/attunement/AttunedAttachments.java` (add `DISCOVERED_CONFLUENCES` attachment + `getDiscoveredConfluences` / `markConfluenceDiscovered` helpers)
- Modify `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java` (append "Confluences" page to `PAGES`, add `Chapter("Confluences", 33)` to `CHAPTERS`, render discovered-vs-redacted rows reading the synced attachment)
- Modify `src/main/resources/assets/attuned/lang/en_us.json` (add `journal.attuned.confluence.*` keys)
- Modify `src/main/java/dev/attuned/synergy/Synergies.java` (call `markConfluenceDiscovered` in `fanfare`; update Task 2's contract test in the same commit)
- Create `src/test/java/dev/attuned/attunement/ConfluenceDiscoveryContractTest.java` (two `@Test` methods: `discoveryAttachmentIsPersistentSyncedCopyOnDeath()` and `journalExposesConfluencesChapterWithDriftGuardAndLang()`)

**Steps**

- [ ] **RED — write the attachment + helpers contract test method.** Create `src/test/java/dev/attuned/attunement/ConfluenceDiscoveryContractTest.java` as a Minecraft-free source-grep test (read source as a `String` via `Files.readString(Path.of(...), StandardCharsets.UTF_8)`, mirror `QuickApplyPresetContractTest`). First method `discoveryAttachmentIsPersistentSyncedCopyOnDeath()` pins these EXACT substrings against `AttunedAttachments.java`:
  - `assertTrue(attachments.contains("public static final AttachmentType<List<String>> DISCOVERED_CONFLUENCES = AttachmentRegistry.create("), "...")`
  - `assertTrue(attachments.contains("Identifier.fromNamespaceAndPath(Attuned.MOD_ID, \"discovered_confluences\")"), "...")`
  - `assertTrue(attachments.contains(".persistent(Codec.STRING.listOf())"), "Discovered confluences must persist across restart")`
  - `assertTrue(attachments.contains(".syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())"), "Discovered confluences must sync to the owning client only")`
  - For `copyOnDeath`, narrow scope first with the `methodRegion(source, start, end)` helper (copy from `FocusDataConsistencyTest`) so the assertion is the `DISCOVERED_CONFLUENCES` block, not a stray `.copyOnDeath()` elsewhere: `String block = methodRegion(attachments, "DISCOVERED_CONFLUENCES = AttachmentRegistry.create(", "public static void init()");` then `assertTrue(block.contains(".copyOnDeath()"), "Discoveries must survive death")`.
  - `assertTrue(attachments.contains("public static List<String> getDiscoveredConfluences(Player player)"), "...")`
  - `assertTrue(attachments.contains("public static void markConfluenceDiscovered(Player player, String id)"), "...")`
  - Reuse the normalizer + freeze: `String body = methodBody(attachments, "public static void markConfluenceDiscovered(Player player, String id)");` (copy `methodBody` helper), `assertTrue(body.contains("normalizedAttachmentId(id)"), "...")`, `assertTrue(body.contains("List.copyOf(updated)"), "...")`, `assertTrue(body.contains("setAttached(DISCOVERED_CONFLUENCES,"), "...")`.

- [ ] **RED run — confirm red.**
  ```
  .\gradlew.bat test --tests "dev.attuned.attunement.ConfluenceDiscoveryContractTest" --no-daemon
  ```
  Must fail on the missing `DISCOVERED_CONFLUENCES` / helper substrings (not on a `methodBody`/`methodRegion` "Missing method signature" from a typo'd prefix — if it does, fix the signature string, not the source).

- [ ] **GREEN — implement the attachment + helpers in `AttunedAttachments.java`.** Add the registration immediately after the `ONBOARDING` block, copying the `PRESETS` synced-list shape but with `Codec.STRING.listOf()` and the String stream codec (no new imports needed — `Codec`, `ByteBufCodecs`, `AttachmentSyncPredicate`, `List` are already imported):
  ```java
  /** Confluence ids this player has discovered (each first activation). Synced for the journal. */
  public static final AttachmentType<List<String>> DISCOVERED_CONFLUENCES = AttachmentRegistry.create(
      Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "discovered_confluences"),
      builder -> builder
          .initializer(() -> List.of())
          .persistent(Codec.STRING.listOf())
          .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
          .copyOnDeath()
  );
  ```
  Then add the two helpers next to `sawOnboarding`/`markOnboarding`, reusing the private `normalizedAttachmentId`:
  ```java
  /** Confluence ids this player has discovered, in discovery order. */
  public static List<String> getDiscoveredConfluences(Player player) {
      return player.getAttachedOrElse(DISCOVERED_CONFLUENCES, List.of());
  }

  /** Records a Confluence id as discovered. A no-op if already discovered. Server-side writes only. */
  public static void markConfluenceDiscovered(Player player, String id) {
      Optional<String> normalized = normalizedAttachmentId(id);
      if (normalized.isEmpty()) {
          return;
      }
      String confluenceId = normalized.get();
      List<String> discovered = player.getAttachedOrElse(DISCOVERED_CONFLUENCES, List.of());
      if (discovered.contains(confluenceId)) {
          return;
      }
      List<String> updated = new ArrayList<>(discovered);
      updated.add(confluenceId);
      player.setAttached(DISCOVERED_CONFLUENCES, List.copyOf(updated));
  }
  ```
  No change to `init()` is needed — it already force-loads the class so all `AttachmentType` statics register.

- [ ] **GREEN — call `markConfluenceDiscovered` from `Synergies.fanfare` (and update Task 2's pin).** In `src/main/java/dev/attuned/synergy/Synergies.java`, add `AttunedAttachments.markConfluenceDiscovered(player, confluenceId);` inside `fanfare(...)` (the one-time path). In `SynergiesRuntimeContractTest`, add `assertTrue(synergies.contains("AttunedAttachments.markConfluenceDiscovered("), "First-discovery fanfare must record the discovery for the journal");` — same commit, preserving the existing assertions.

- [ ] **GREEN — run the attachment method.**
  ```
  .\gradlew.bat test --tests "dev.attuned.attunement.ConfluenceDiscoveryContractTest" --no-daemon
  ```
  The attachment + helper assertions pass. The journal/lang assertions (next method) will still fail; if you have already written that method, run only the attachment method: `--tests "dev.attuned.attunement.ConfluenceDiscoveryContractTest.discoveryAttachmentIsPersistentSyncedCopyOnDeath"`.

- [ ] **RED — write the journal-chapter + drift-guard + lang test method.** Add `journalExposesConfluencesChapterWithDriftGuardAndLang()` to the same test class (reuse `Files.readString` of `AttunementJournalScreen.java` and `en_us.json`). Pin these EXACT substrings:
  - Chapter registered: `assertTrue(screenSource.contains("new Chapter(\"Confluences\""), "Journal should expose Confluences as its own chapter")`
  - Page entry carries the matching chapter name (drift-guard precondition): `assertTrue(screenSource.contains("new Page(\"Confluences\", \"journal.attuned.confluence.intro\""), "Confluences chapter needs a landing page declaring the same chapter name")`
  - Ordering — the new page is appended at the END of `PAGES`, after the current last page `journal.attuned.page28` (the HUD page, index 32): `assertTrue(screenSource.indexOf("journal.attuned.page28") < screenSource.indexOf("journal.attuned.confluence.intro"), "Confluences page must be appended at the end of PAGES so existing chapter indices do not shift")`
  - Drift guard still present (extend, do not remove the generic fail-fast loop): `assertTrue(screenSource.contains("for (Chapter chapter : CHAPTERS)"), "The chapter-drift fail-fast guard must keep iterating every chapter")` (anchor on the loop, not the brittle exception text).
  - Synced attachment read on the render path: `assertTrue(screenSource.contains("AttunedAttachments.getDiscoveredConfluences("), "Confluences page must read the synced discovery attachment to choose discovered vs redacted rows")`
  - Redacted fallback key present: `assertTrue(screenSource.contains("journal.attuned.confluence.redacted"), "Undiscovered Confluences must render the redacted ??? row")`
  - Lang keys in `en_us.json` (flat quoted keys): `assertTrue(lang.contains("\"journal.attuned.confluence.intro\""), ...)`, `.contains("\"journal.attuned.confluence.title\"")`, `.contains("\"journal.attuned.confluence.redacted\"")`, `.contains("\"journal.attuned.confluence.members\"")` (the "%s Foci" member-count line).

  > VERIFY: the redacted-row render needs the per-Confluence member count, which requires the loaded `SynergyDefinition` table client-side. It is available via `Minecraft.getInstance().level.registryAccess().lookupOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS)` (mirror `Attunement.java:57-58`, iterate with `registry.listElements().forEach(...)` per `AttunedCommands.java:145-146`). If Task 1's registry key name differs, update this assertion's symbol and the screen code to match.

- [ ] **RED run — confirm red.**
  ```
  .\gradlew.bat test --tests "dev.attuned.attunement.ConfluenceDiscoveryContractTest" --no-daemon
  ```
  The new method must fail on the missing `Chapter("Confluences")`, the missing `getDiscoveredConfluences` read in the screen, and the missing lang keys.

- [ ] **GREEN — append the Confluences page + chapter to `AttunementJournalScreen.java`.** Edit the `PAGES` `List.of(...)`: append, after the current last entry `new Page("HUD", "journal.attuned.page28", 0xFF95E6B3, null)` (add a trailing comma to it), the new final entry:
  ```java
  new Page("Confluences", "journal.attuned.confluence.intro", 0xFF95E6B3, null)
  ```
  This becomes index 33 (PAGES currently has 33 entries, indices 0-32). Then add to the `CHAPTERS` `List.of(...)`, after `new Chapter("HUD", 32)` (add a trailing comma to it):
  ```java
  new Chapter("Confluences", 33)
  ```
  The existing static drift guard validates `PAGES.get(33).chapter().equals("Confluences")` automatically — it passes. **Do NOT reorder or insert mid-list** (that would silently shift HUD's `firstPage=32`); append only.

- [ ] **GREEN — implement discovered-vs-redacted row rendering.** In the page-render path (the `drawPage`/`pageText` region) special-case the Confluences page: read `LocalPlayer player = Minecraft.getInstance().player;` (guard `player != null`), `Set<String> discovered = new HashSet<>(AttunedAttachments.getDiscoveredConfluences(player));`, and the loaded `SynergyDefinition` table from the client registry (`...registryAccess().lookupOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS)`, iterate via `registry.listElements().forEach(...)`). For each known Confluence (key it by `registry.getKey(def).toString()` for the discovered check and `getPath()` for the lang lookups, matching Task 2's `pathOf` discipline):
  - discovered → render `confluence.attuned.<path>.name` + members + `confluence.attuned.<path>.desc`
  - not discovered → render `journal.attuned.confluence.redacted` (the `???` row) + `journal.attuned.confluence.members` with `def.members().size()` (count only — do NOT reveal member identities)

  Add imports: `net.minecraft.client.Minecraft`, `net.minecraft.client.player.LocalPlayer`, `dev.attuned.attunement.AttunedAttachments`, `dev.attuned.AttunedRegistries`, `dev.attuned.api.synergy.SynergyDefinition`, `net.minecraft.core.Registry`, `java.util.HashSet`, `java.util.Set`.

  > VERIFY the client-side translation accessor: confirm whether the screen uses `I18n.get(String, Object...)` or `Component.translatable(...).getString()` by checking the prevailing idiom already in `AttunementJournalScreen.java` (grep `I18n.get` vs `Component.translatable` in that file) and match it. If `I18n` is not available in this fork, use `Component.translatable("journal.attuned.confluence.members", count).getString()`.

- [ ] **GREEN — add the lang keys to `en_us.json`.** Insert these flat keys (follow the existing `journal.attuned.*` block formatting; keep journal copy short — the contract caps page copy at ~190 chars):
  ```json
  "journal.attuned.confluence.intro": "Where two currents meet, a Confluence wakes. Run the right Foci together to discover them.",
  "journal.attuned.confluence.title": "Confluences",
  "journal.attuned.confluence.redacted": "??? — undiscovered",
  "journal.attuned.confluence.members": "%s Foci"
  ```
  Per-Confluence `confluence.attuned.<name>.name` / `.desc` keys are owned by Task 2's lang + Task 5's content sweep — do NOT duplicate them here; this task adds only the chapter/journal/discovery keys above.

- [ ] **GREEN — run the full contract test.**
  ```
  .\gradlew.bat test --tests "dev.attuned.attunement.ConfluenceDiscoveryContractTest" --no-daemon
  ```
  Both `@Test` methods pass.

- [ ] **GREEN — journal regression test stays green.**
  ```
  .\gradlew.bat test --tests "dev.attuned.content.AttunementJournalUiContractTest" --no-daemon
  ```
  The appended page/chapter must NOT break the pinned `CHAPTERS = List.of`, `new Chapter("Offshore"...`, page-ordering, or HUD/Offshore/Revenant assertions (appending at the end preserves every existing index). If it goes red, the cause is a mid-list insert that shifted indices — revert to append-only.

- [ ] **GREEN — full-suite gate.**
  ```
  .\gradlew.bat test --no-daemon
  ```
  Confirm no other contract test (attachment-count or lang-sweep) regressed from the new attachment/lang keys.

- [ ] **SMOKE GATE — `runClient` visual confirmation.**
  ```
  .\gradlew.bat runClient --no-daemon
  ```
  Open the Attunement Journal, navigate to the new **Confluences** chapter, and confirm: (a) the chapter renders without the static-init `IllegalStateException` (the drift guard passing at class load); (b) before any discovery, every row shows `??? — undiscovered` + `N Foci` count; (c) after Task 2 writes a first discovery (equip a member pair so the Confluence wakes), the corresponding row flips to the real name/members/effect — verifying the `targetOnly` sync reaches the client journal render. This is the only gate that exercises the synced-attachment read path end-to-end.

**Files touched by Task 3 (all absolute):**
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\attunement\AttunedAttachments.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\client\java\dev\attuned\client\screen\AttunementJournalScreen.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\resources\assets\attuned\lang\en_us.json` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\synergy\Synergies.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\synergy\SynergiesRuntimeContractTest.java` (modify — same commit)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\attunement\ConfluenceDiscoveryContractTest.java` (new)

---

### Task 4: Ship the first authored Confluences (content) + SynergyDefinition consistency contract

Depends on **Tasks 1–3**. With the registry, runtime, attachment, and journal in place, ship the first authored `synergy/*.json` Confluences and a `FocusDataConsistencyTest`-style sweep that gates them. This is the content slice — every shipped Confluence must reference real, registered member Foci; have its `confluence.attuned.<name>.name`/`.desc` lang keys; have a `confluence_<name>.json` advancement; and (if it carries a `behavior`) reference a registered `FocusBehavior`. Confluences add **zero** Foci, so the README `N Foci` count is untouched.

**Files**
- Create `src/main/resources/data/attuned/attuned/synergy/<name>.json` (one per Confluence — note the double `attuned/attuned` nesting, mirroring `data/attuned/attuned/focus/`)
- Create `src/test/java/dev/attuned/synergy/SynergyDefinitionContractTest.java` (sweeps the synergy data dir; pins lang + advancement + member-Focus existence)
- Modify `src/main/resources/assets/attuned/lang/en_us.json` (finalize the per-Confluence `.name`/`.desc` pairs to exactly match the shipped ids)
- Modify `docs/reference.md` (Confluences section + a row per shipped Confluence)
- Possibly modify `src/main/java/dev/attuned/content/AttunedFocusBehaviors.java` (only if a Confluence ships a code behavior — register it via `register("<name>", new <Name>Behavior())`)

**Steps**

- [ ] **RED — write `SynergyDefinitionContractTest`.** Create `src/test/java/dev/attuned/synergy/SynergyDefinitionContractTest.java`, modelled on `FocusDataConsistencyTest`'s content-sweep pattern. Define `private static final Path SYNERGY_DATA_DIR = Path.of("src/main/resources/data/attuned/attuned/synergy");` and `LANG_FILE`. Pin:
  - The directory exists and is non-empty (`Files.isDirectory(SYNERGY_DATA_DIR)` and at least one `.json` via `Files.list(...).filter(p -> p.toString().endsWith(".json"))`).
  - For each `synergy/<name>.json`: parse it (`JsonParser.parseString(Files.readString(...))`), assert `"members"` is a non-empty array of 2–3 namespaced ids, each matching the `NAMESPACED_ID` pattern.
  - **Member-Focus existence:** each member id must correspond to a shipped `focus/*.json` — collect the set of `"item"` ids from `data/attuned/attuned/focus/*.json` and assert every Confluence member is in it. (A Confluence may only reference Foci that exist.)
  - **Lang coverage:** for each Confluence `<name>`, assert `lang.contains("\"confluence.attuned." + name + ".name\"")` and `.contains("\"confluence.attuned." + name + ".desc\"")`.
  - **Advancement coverage:** assert `Files.isRegularFile(Path.of("src/main/resources/data/attuned/advancement/attunement/confluence_" + name + ".json"))`.
  - **Behavior registration (if present):** if a Confluence JSON has a `"behavior"` field, assert that id appears as a `register("<id>", ...)` in `AttunedFocusBehaviors.java` source (reuse the `registeredBehaviorIds(source)` idiom from `FocusDataConsistencyTest`).
  - **Docs coverage:** assert `docs/reference.md` content `.contains("Confluences")` and a row/mention per shipped Confluence `<name>`.

- [ ] **RED run — confirm red.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyDefinitionContractTest" --no-daemon
  ```
  Fails: no `synergy/*.json` files, missing lang/advancement/docs coverage.

- [ ] **GREEN — author the first Confluences.** Create 2–4 `src/main/resources/data/attuned/attuned/synergy/<name>.json` files referencing real member Foci (use exact `"item"` ids from existing `focus/*.json`). Each is a small, modest set bonus, e.g.:
  ```json
  {
    "members": ["attuned:lantern_focus", "attuned:veil_focus"],
    "modifiers": [
      {
        "attribute": "minecraft:generic.movement_speed",
        "amount": 0.05,
        "operation": "add_multiplied_total"
      }
    ]
  }
  ```
  Keep amounts modest (the feature is a small reward, not a power spike). Omit `behavior` unless a code behavior is genuinely needed; a pure-modifiers Confluence needs no `AttunedFocusBehaviors` change. **VERIFY** the exact attribute id strings and `AttributeModifier.Operation` enum names against an existing `focus/*.json` that carries modifiers (do not guess `add_multiplied_total` vs `multiply_total` — copy from a shipped Focus def).

- [ ] **GREEN — finalize lang, advancements, docs.**
  - In `en_us.json`, ensure each shipped Confluence has `"confluence.attuned.<name>.name"` and `"confluence.attuned.<name>.desc"` (replace any Task 2 placeholders so the ids match exactly).
  - Ensure each `confluence_<name>.json` advancement exists (created in Task 2 — add any missing ones, copying the verified pact shape).
  - In `docs/reference.md`, add a **Confluences** section (slotting after Behaviors, before Commands) documenting: what a Confluence is (a no-budget set bonus that wakes when 2–3 named Foci are all active), the datapack file location `data/<ns>/attuned/synergy/<name>.json`, the `members`/`modifiers`/`behavior` fields, the discovery/journal meta, and a row per shipped Confluence (members + effect). Also add a line in "Where everything lives" pointing at `SynergyDefinition`, `SynergyResolver`, and `Synergies`.

- [ ] **GREEN — run the content test.**
  ```
  .\gradlew.bat test --tests "dev.attuned.synergy.SynergyDefinitionContractTest" --no-daemon
  ```
  Passes.

- [ ] **GREEN — repository + full gate.**
  ```
  .\gradlew.bat test --no-daemon
  python tools/verify_repository.py
  ```
  Confirm `verify_repository.py` stays green — Confluences add zero `focus/*.json` files, so the README `N Foci` count and the 8 Modrinth gallery PNGs are unaffected (the optional Confluences gallery panel is NOT required for v1). JSON-parseable and no-TODO gates must stay green.

- [ ] **SMOKE GATE — `runClient` end-to-end.**
  ```
  .\gradlew.bat runClient --no-daemon
  ```
  In a creative test world, equip a shipped Confluence's full member set (within budget, all active). Confirm: the chat announce fires, the first-discovery fanfare + journal flip happen once, the modifier applies (check via F3 attribute screen or `/attuned` readout), and unequipping a member removes the modifier with no stale attribute lingering.

**Files touched by Task 4 (all absolute):**
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\resources\data\attuned\attuned\synergy\<name>.json` (new, one per Confluence)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\synergy\SynergyDefinitionContractTest.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\resources\assets\attuned\lang\en_us.json` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\docs\reference.md` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\content\AttunedFocusBehaviors.java` (modify — only if a Confluence ships a behavior)

---

### Task 5: Readout indicator + previewOf build hint

The lowest-risk, purely-client polish slice. Depends on **Tasks 1–4**. Adds the `Snapshot.activeConfluences` field, the `FociHud` count chip, and a "one member away from a discovered Confluence" preview hint. Keep every change cleanly separable: the snapshot field + HUD chip can ship without the preview hint, and vice-versa. Neither touches the budget core, server tick loop, or any pinned non-confluence string.

**Files**
- Modify `src/client/java/dev/attuned/client/AttunementReadout.java` — add `Set<String> activeConfluences` to `Snapshot`, compute it client-side in `snapshot(Player)`.
- Modify `src/client/java/dev/attuned/client/hud/FociHud.java` — draw a small confluence count chip from `readout.activeConfluences()` using `graphics.fill()` only.
- Modify `src/main/java/dev/attuned/synergy/SynergyResolver.java` — add the pure `previewOf(Set<String> active, Set<String> discovered, List<SynergyDef> defs) : Optional<String>` policy.
- Modify `src/main/java/dev/attuned/synergy/Synergies.java` — add the Minecraft-facing `previewOf(Player) : Optional<Component>` wrapper (mirroring `Pacts.previewOf`).
- Modify `src/test/java/dev/attuned/client/FociHudContractTest.java` — add a confluence-chip pin; preserve all existing pinned strings.
- Create `src/test/java/dev/attuned/synergy/SynergyPreviewResolverTest.java` — Minecraft-free behavioral test for the previewOf policy.
- Create `src/test/java/dev/attuned/client/AttunementReadoutContractTest.java` — source-grep pin for the new Snapshot field (only if no readout contract test already exists; otherwise extend the existing one — VERIFY via `Glob src/test/java/dev/attuned/client/*Readout*`).
- Modify `src/main/resources/assets/attuned/lang/en_us.json` — add `confluence.attuned.preview`.

**Steps**

- [ ] **RED — pin `Snapshot.activeConfluences`.** VERIFY whether a readout contract test exists (`Glob` for `src/test/java/dev/attuned/client/*Readout*`). If yes, add a method to it; else create `src/test/java/dev/attuned/client/AttunementReadoutContractTest.java`. Add a source-grep method `snapshotExposesActiveConfluences()` reading `AttunementReadout.java` and asserting:
  - `assertTrue(src.contains("Set<String> activeConfluences"), "Snapshot must carry the active-confluence id set for the HUD.");`
  - `assertTrue(src.contains("activeConfluences = Set.copyOf(activeConfluences)"), "Snapshot must defensively copy the confluence set like activeSlots/dormantReasons.");`
  - `assertTrue(src.contains("SynergyResolver.activeConfluences("), "snapshot() must compute confluences via the Minecraft-free resolver.");`

  This is a source-grep test (not behavioral) because constructing a `Snapshot` needs a bound `Player`/registry, which the unit classpath cannot Bootstrap.

- [ ] **RED run.**
  ```
  .\gradlew.bat cleanTest test --tests "dev.attuned.client.AttunementReadoutContractTest" --no-daemon
  ```
  Fails on the missing `Set<String> activeConfluences` string.

- [ ] **GREEN — implement the field in `AttunementReadout.java`.**
  - Add `import java.util.HashSet;`, `import java.util.Set;` (if not present), `import dev.attuned.synergy.SynergyResolver;`, `import dev.attuned.synergy.Synergies;`, `import net.minecraft.core.registries.BuiltInRegistries;`.
  - Append `Set<String> activeConfluences` as the LAST field of the `Snapshot` record (search for `public record Snapshot`). In the compact constructor add `activeConfluences = Set.copyOf(activeConfluences);`.
  - In `snapshot(Player)`, reuse the existing `activeSlots` loop: build `Set<String> activeFocusIds` by resolving each active slot's `FocusDefinition` and adding `BuiltInRegistries.ITEM.getKey(focus.item().value()).toString()` (the same `.toString()` bridge as Task 2). Then call `SynergyResolver.activeConfluences(activeFocusIds, Synergies.definitions(player))` — where `Synergies.definitions(Player)` is a small client-safe accessor that reads the synced `SYNERGY_DEFINITIONS` registry and returns the resolver's `List<SynergyDef>` (add it to `Synergies` in this task if it does not already exist; it factors out the registry→`SynergyDef` bridge from Task 2's `tickPlayer` so both share one code path). Client-side resolution is safe: `Attunement.resolution(player)` already branches `isClientSide()`, and `SynergyResolver` is Minecraft-free.
  - Pass `activeConfluences` as the final constructor arg in the `return new Snapshot(...)`.
  - **Do NOT add a second `Attunement.resolution(player)` or `AttunementReadout.cached(player)` call** — `FociHudContractTest` pins exactly one resolution per frame.

- [ ] **GREEN run + readout/HUD regression.**
  ```
  .\gradlew.bat cleanTest test --tests "dev.attuned.client.AttunementReadoutContractTest" --tests "dev.attuned.client.FociHudContractTest" --no-daemon
  ```
  The record-arg change must not touch any pinned FociHud string yet — both pass.

- [ ] **RED — pin the HUD confluence chip.** In `FociHudContractTest.java`, in the test that pins the equipped-stack/dormant draw, append (preserving every existing assertion verbatim):
  - `assertTrue(hud.contains("readout.activeConfluences()"), "The Foci HUD should read the active-confluence set from the shared readout.");`
  - `assertTrue(hud.contains("drawConfluenceChip"), "The Foci HUD should paint a confluence count chip.");`
  Leave the existing `assertEquals(1, countOccurrences(hud, "AttunementReadout.cached(player)"), ...)` pin untouched — the chip must reuse the same `readout` local, adding NO new `cached()`/`resolution()` call. Do NOT alter the existing pinned strings (`FOCUS_GRID_COLUMNS = 2`, `APEX_BAR_W = 54`, `BAR_EMPTY_FILL`, `drawApexBar`, `drawDormantOverlay`, etc.) — the chip is purely additive.

- [ ] **RED run.**
  ```
  .\gradlew.bat cleanTest test --tests "dev.attuned.client.FociHudContractTest" --no-daemon
  ```
  Fails on the two new chip strings.

- [ ] **GREEN — implement `drawConfluenceChip` in `FociHud.java`.**
  - In `draw(...)`, after the existing `AttunementReadout.Snapshot readout = AttunementReadout.cached(player);` and the focus-grid draw, add: `drawConfluenceChip(graphics, readout.activeConfluences().size(), x + HUD_W - <chipW>, y + <chipY>);` reusing the `x`/`y`/`HUD_W` already in scope (inheriting the `pose().scale()` push).
  - Implement `private static void drawConfluenceChip(GuiGraphicsExtractor graphics, int count, int x, int y)` using ONLY `graphics.fill(...)` — there is no text rendering anywhere in the `hud` package. Render the count as up to N pip dots (e.g. one 2×2 filled square per active Confluence, clamped to a sensible max), plus a 1px frame using existing palette constants (`FRAME_EDGE`/`FRAME_GLOW`). When `count == 0`, early-return (draw nothing) so the chip never clutters an un-synergized build. Mirror the rectangle style of `drawActiveGlow`/`drawDormantOverlay`.
  - **VERIFY** there is no `drawString`/`Font` on `GuiGraphicsExtractor` before assuming text is impossible:
    ```
    python -c "import zipfile,glob,re; j=glob.glob(r'C:/Users/Eating/.gradle/caches/fabric-loom/**/minecraft-client.jar', recursive=True)[0]; z=zipfile.ZipFile(j); d=z.read('net/minecraft/client/gui/GuiGraphicsExtractor.class'); print([s for s in re.findall(rb'[ -~]{4,}', d) if b'String' in s or b'text' in s])"
    ```
    If a draw-text method exists, the chip MAY render the numeral — but keep it `fill`-only to stay within the proven HUD idiom and avoid a font-batch flush mid-layer.

- [ ] **GREEN run.**
  ```
  .\gradlew.bat cleanTest test --tests "dev.attuned.client.FociHudContractTest" --no-daemon
  ```
  All old + new pins pass.

- [ ] **RED — write the Minecraft-free preview policy test.** Create `src/test/java/dev/attuned/synergy/SynergyPreviewResolverTest.java`, modelled on `PresetApplicationResolverTest` (String ids, no Bootstrap, JUnit 5). The pure decision: "given active Focus ids, discovered Confluence ids, and the def table, return the single Confluence exactly one active member short — but only if it is discovered." Cases:
  - `oneMemberAwayFromDiscoveredConfluenceIsPreviewed()` — active `{"attuned:lantern_focus"}`, discovered `{"attuned:hunters_patience"}`, def members `["attuned:lantern_focus","attuned:veil_focus"]` → `assertEquals(Optional.of("attuned:hunters_patience"), result, "A discovered confluence one active member short should be previewed.");`
  - `undiscoveredConfluenceIsNeverPreviewed()` — same active/def, discovered `{}` → `assertTrue(result.isEmpty(), "An undiscovered confluence must never be hinted, so the meta is not spoiled.");`
  - `fullyActiveConfluenceIsNotPreviewed()` — active `{"attuned:lantern_focus","attuned:veil_focus"}` → `assertTrue(result.isEmpty(), "An already-active confluence is not a 'needs one more' hint.");`
  - `twoOrMoreMembersAwayIsNotPreviewed()` — active `{}`, 2-member def, discovered → `assertTrue(result.isEmpty(), "A confluence two members short is too far to hint.");`
  - `threeMemberConfluenceOneAwayIsPreviewed()` — active 2 of 3, discovered → present.

- [ ] **RED run.**
  ```
  .\gradlew.bat cleanTest test --tests "dev.attuned.synergy.SynergyPreviewResolverTest" --no-daemon
  ```
  Fails (method does not exist).

- [ ] **GREEN — implement the preview policy.**
  - Add to `SynergyResolver.java`: `public static Optional<String> previewOf(Set<String> active, Set<String> discovered, List<SynergyDef> defs)`. Logic: for each `def` whose `id` is in `discovered` and which is NOT already fully active (`!active.containsAll(def.members())`), count missing members; if **exactly one** def is exactly one member short, return `Optional.of(def.id())`; otherwise `Optional.empty()`. Keep it pure (no Minecraft types).
  - Add the Minecraft-facing wrapper to `Synergies.java`, mirroring `Pacts.previewOf(Player)`: `public static Optional<Component> previewOf(Player player)` that builds the active-id set + discovered set (`AttunedAttachments.getDiscoveredConfluences(player)`) + the def table (`Synergies.definitions(player)`), calls `SynergyResolver.previewOf(...)`, and on a present result builds `Component.translatable("confluence.attuned.preview", <confluenceName>, <missingMemberName>).withStyle(ChatFormatting.GRAY)` where both args are already `Component`-typed (`<confluenceName>` = `Component.translatable("confluence.attuned." + path + ".name")`; `<missingMemberName>` = the missing member's item lang key `Component.translatable("item.attuned." + missingPath)`). Surface the hint in the readout tooltip/panel next to the existing `Next Pact:` line if the panel wants it; otherwise expose the `Optional<Component>` for the panel to consume.
  - Add the lang key to `en_us.json` (flat object): `"confluence.attuned.preview": "%1$s needs %2$s"`.

- [ ] **GREEN run + cross-regression.**
  ```
  .\gradlew.bat cleanTest test --tests "dev.attuned.synergy.SynergyPreviewResolverTest" --tests "dev.attuned.client.FociHudContractTest" --tests "dev.attuned.synergy.SynergiesRuntimeContractTest" --tests "dev.attuned.synergy.SynergyResolverTest" --no-daemon
  ```
  Passes — no cross-regression from the new resolver method.

- [ ] **GREEN — docs ride-along.** Update `docs/reference.md`: document the HUD count chip and the discovered-only preview hint in the Confluences / HUD section (the hint only ever surfaces for **already-discovered** Confluences — meta is never spoiled). Confirm no Confluence is referenced without its three lang keys (`.name`, `.desc`, and the generic transition keys) or the content sweep in Task 4 fails.

**Separability note:** the snapshot field + HUD chip (first half) and the `previewOf` hint (second half) are independent; either can be reverted without touching the other.

**Files touched by Task 5 (all absolute):**
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\client\java\dev\attuned\client\AttunementReadout.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\client\java\dev\attuned\client\hud\FociHud.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\synergy\SynergyResolver.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\java\dev\attuned\synergy\Synergies.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\client\FociHudContractTest.java` (modify)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\synergy\SynergyPreviewResolverTest.java` (new)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\test\java\dev\attuned\client\AttunementReadoutContractTest.java` (new or extend existing)
- `C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\src\main\resources\assets\attuned\lang\en_us.json` (modify)

---

### Task: Release

Final verification gate, manual `runClient` checklist, and changelog/version. **Publish only on explicit user instruction** — do not push, tag, or run any publish script otherwise.

- [ ] **Docs/lang/changelog ride-along.**
  - `CHANGELOG.md`: under a `## Attuned <version>` heading at the TOP, add `### Added` bullets, e.g.:
    - `- **Focus Confluences** — small set bonuses that wake when a specific 2–3 Focus combination is all active, with a discovery/journal collection meta.`
    - `- **Confluence HUD indicator** — the Foci panel now shows how many Confluences are active.`
    - `- **Build-craft hint** — a discovered Confluence one Focus away surfaces a one-line panel hint.`
  - Confirm `docs/reference.md` has the Confluences section + per-Confluence rows (Task 4) and the HUD/hint documentation (Task 5).
  - Confirm `README.md` — add one line on the Confluence meta if it is a headline feature; **do NOT change the advertised `N Foci` count** (Confluences add zero Foci).

- [ ] **Full verification gate (run in order; force `cleanTest` — UP-TO-DATE lies):**
  ```
  .\gradlew.bat cleanTest build --no-daemon
  python tools/verify_repository.py
  python -m unittest discover -s tests
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  git diff --check
  ```
  All must pass. `verify_repository.py` must stay green: the README `N Foci` count is unchanged, the 8 Modrinth gallery PNGs are unchanged (the optional Confluences gallery panel is NOT required for v1), and JSON-parseable / no-TODO / no-secret gates hold. The runtime smoke confirms a clean Fabric mod-init boot with the new registry + advancements + datapack Confluences loaded.

- [ ] **Manual `runClient` checklist** (`.\gradlew.bat runClient --no-daemon`), walking the full Confluence loop:
  1. In a creative test world, equip a known member pair so both Foci are **active** (within budget, not just equipped). Confirm: chat announce fires ("Confluence discovered: …"), a journal entry appears in the new "Confluences" chapter (not redacted `???`), and the Foci HUD chip increments to 1.
  2. Unequip one member → chip returns to 0, the modifier is removed (verify via `/attuned` readout or the F3 attribute screen that the `confluence_<id>` modifier is gone — strip-then-reapply discipline).
  3. Re-equip to wake it, then `/kill`. After respawn confirm: the discovery STILL shows in the journal as discovered (`DISCOVERED_CONFLUENCES` is `copyOnDeath`), and the second activation does NOT re-fire the first-discovery fanfare (`sawOnboarding` keyed `"confluence_first_<id>"` already set).
  4. Remove exactly one member of an already-discovered Confluence so the build is one active member short → confirm the `previewOf` hint line ("<Name> needs <Member>") appears, and that an **undiscovered** Confluence one-away shows NO hint (meta not spoiled).
  5. Toggle the Foci HUD off → the chip disappears with the panel, but the chat announce + journal still carry the feature.

- [ ] **Publish (ONLY on explicit user instruction).** Bump `mod_version` in `gradle.properties`, re-run the full gate, then run the project's publish flow (Modrinth task / `tools/publish_curseforge.py`). Stage explicit paths only (never `git add -A`/`.`). Never attribute Claude in any commit, co-authored-by, PR text, or other VCS artifact. Before pushing, ensure every new committed file (`Synergies.java`, `SynergyResolver.java`, `SynergyDefinition.java`, all new tests, the `synergy/*.json` + advancement JSONs) is tracked, or the pre-push `tools/check_untracked_references.py` hook trips ("passes locally, fails CI").

---

## Open questions / VERIFY-before-coding

Resolve these fork-specific API/naming questions before the relevant task's GREEN step — most surface at compile time, but resolving them up front avoids churn:

1. **`Identifier.CODEC` is registry-free (Task 1).** Confirm `Identifier.CODEC` encodes a `SynergyDefinition.members` list of raw `Identifier`s as plain strings without touching item registries (it should — `FocusDefinition.java:49` uses it for `faction` with no Bootstrap). If the codec round-trip test fails with a registry-Bootstrap error, re-check before swapping to `Codec.STRING`.
2. **Attribute/operation id strings in authored Confluences (Task 4).** Do NOT guess attribute ids (`minecraft:generic.movement_speed` vs `minecraft:movement_speed`) or `AttributeModifier.Operation` enum names (`add_multiplied_total` vs `multiply_total`) — copy them verbatim from an existing `focus/*.json` that carries `modifiers`.
3. **JOIN handler field name (Task 2).** Confirmed `(handler, sender, server) -> { ServerPlayer player = handler.player; ... }` (`Pacts.java:145-146`). If the fork's `ServerGamePacketListenerImpl` field is not `.player`, extract it from the dev jar.
4. **`getAttribute` / `addTransientModifier` / `removeModifier` signatures (Task 2).** All copied from `AttunedEffects.java:266-287`. If `compileJava` fails on any, run the jar-string extraction in the Task 2 VERIFY block and correct the single symbol + its pinned contract-test string in the same commit.
5. **`GuiGraphicsExtractor` has no `drawString`/`Font` (Task 5).** Run the `minecraft-client.jar` string extraction to confirm before assuming the chip must be `fill`-only pips. If a text method exists, the chip MAY render a numeral — but stay `fill`-only to match the proven HUD idiom.
6. **Client-side translation accessor (Task 3).** Confirm whether `AttunementJournalScreen` uses `I18n.get(String, Object...)` or `Component.translatable(...).getString()` and match the prevailing idiom for the discovered/redacted rows.
7. **`SYNERGY_DEFINITIONS` registry key name consistency (Tasks 3 & 5).** Tasks 3 and 5 read the registry client-side by the exact key declared in Task 1. If Task 1's key name is changed from `SYNERGY_DEFINITIONS`, update every downstream symbol and pinned assertion to match.
8. **Pact advancement trigger type (Task 2).** Read `data/attuned/advancement/attunement/pact_pyresworn.json` and confirm it uses `"trigger": "minecraft:impossible"` (code-granted), then copy that exact shape into each `confluence_<name>.json` so the advancement is awarded programmatically, not auto-earned.
9. **`Synergies.definitions(Player)` shared bridge (Tasks 2 & 5).** The registry→`List<SynergyDef>` bridge (`Identifier::toString` on members, registry-key string as `SynergyDef.id()`) is used by both the runtime tick loop and the client readout. Factor it into one `Synergies.definitions(Player)` accessor so both call sites stay consistent and there is a single source of truth for the id-bridging convention.