# Adding a new Focus

This is a step-by-step recipe. Follow it top to bottom and you will have a
working Focus. It uses one running example — a **Stoneskin Focus** that grants
extra armor — so every step shows real text you can adapt.

> **Before you start:** pick your Focus's name in `lower_case_with_underscores`.
> The example uses `stoneskin_focus`. Use *your* name everywhere this guide says
> `stoneskin_focus`, spelled identically every time.

A Focus is made of small files in two places:

- **Code & data** under `src/main/...` — makes the Focus exist.
- **Assets** under `src/main/resources/assets/...` — makes it look right.

Steps 1–6 are needed by **every** Focus. Step 7 is **only** for Foci with a
special power. Each step is one file or one small edit.

---

## Step 1 — Register the item

**File:** `src/main/java/dev/attuned/content/AttunedContent.java`

Find the block of lines that look like `public static final Item ... = register(...)`.
Add one more line next to them:

```java
public static final Item STONESKIN_FOCUS = register("stoneskin_focus");
```

- The text in quotes is your Focus's name. It must match every other file.
- The `UPPERCASE_NAME` on the left is how the rest of the code refers to it.

That is the whole item. `register` already makes it stack to 1, like every Focus.

## Step 2 — Put it in the creative menu

**File:** the same `AttunedContent.java`

Find the method `registerCreativeTab()`. Inside it is a list of
`output.accept(...)` lines. Add yours:

```java
output.accept(STONESKIN_FOCUS);
```

Now the Focus appears in the **Attuned** creative-inventory tab.

> For a **stat-only Focus, `AttunedContent.java` is the only `.java` file you
> touch.** Everything below is plain text and images.

## Step 3 — Create the Focus definition

This file sets the cost, the affinity, and the stats. It is the heart of a Focus.

**Create:** `src/main/resources/data/attuned/attuned/focus/stoneskin_focus.json`

```json
{
	"item": "attuned:stoneskin_focus",
	"cost": 3,
	"affinity": "bastion",
	"modifiers": [
		{
			"attribute": "minecraft:armor",
			"amount": 2,
			"operation": "add_value"
		}
	]
}
```

- `item` — always `attuned:` followed by your name. **Required.**
- `cost` — attunement points this Focus uses, usually 2–6. Defaults to 1 if left out.
- `affinity` — `fury`, `bastion`, or `zephyr`. **Leave this line out** for a
  neutral utility Focus.
- `modifiers` — the stat changes. Leave it out entirely if the Focus has no
  stats (a pure power Focus). See [reference.md](reference.md#attribute-modifiers)
  for the full list of attributes and what `operation` means.

## Step 4 — Create the two model files

These tell Minecraft how to draw the item. They are identical for every Focus
except the name — the easiest way is to copy an existing Focus's two files and
rename them.

**Create:** `src/main/resources/assets/attuned/items/stoneskin_focus.json`

```json
{
	"model": {
		"type": "minecraft:model",
		"model": "attuned:item/stoneskin_focus"
	}
}
```

**Create:** `src/main/resources/assets/attuned/models/item/stoneskin_focus.json`

```json
{
	"parent": "minecraft:item/generated",
	"textures": {
		"layer0": "attuned:item/stoneskin_focus"
	}
}
```

## Step 5 — Add a texture

**Create:** `src/main/resources/assets/attuned/textures/item/stoneskin_focus.png`

A 16×16 PNG image. If you do not have art yet, copy any existing Focus texture
and rename it — you can replace it with real art later. Without this file the
item shows the black-and-purple "missing texture" pattern (it still works).

## Step 6 — Add the names and text

**File:** `src/main/resources/assets/attuned/lang/en_us.json`

Add these four lines (mind the commas — every line needs a trailing comma except
the very last line in the file):

```json
"item.attuned.stoneskin_focus": "Stoneskin Focus",
"item.attuned.stoneskin_focus.lore": "Skin remembers the mountain.",
"item.attuned.stoneskin_focus.lore2": "Blows arrive and find it unbothered.",
"item.attuned.stoneskin_focus.effect": "Grants +2 armor.",
```

- The first line is the item's display name.
- `.lore` and `.lore2` are two italic flavour lines shown on the tooltip.
- `.effect` is the green line that tells the player what the Focus does.

**That is a complete, working Focus.** If it only changes stats, you are done —
skip to [Test your Focus](#test-your-focus).

---

## Step 7 — Give it a special power (optional, needs code)

Only do this if your Focus does something a stat cannot — call lightning,
teleport, grant night vision, and so on. It adds one Java file.

### 7a — Write the behavior

**Create:** `src/main/java/dev/attuned/content/behavior/StoneskinBehavior.java`

A behavior implements `FocusBehavior`, which gives you three optional hooks:

```java
package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class StoneskinBehavior implements FocusBehavior {

	@Override
	public void onActivate(ServerPlayer player, ItemStack focus) {
		// Runs once, the moment the Focus becomes active.
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		// Runs every server tick (20 times a second) while the Focus is active.
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		// Runs once, when the Focus stops being active. Undo anything here.
	}
}
```

You only need to write the hooks you actually use. The simplest real example in
the codebase is `DelverBehavior.java` (one short `onTick`) — read it first.

### 7b — Register the behavior

**File:** `src/main/java/dev/attuned/content/AttunedContent.java`, in `init()`

Add a line next to the other `registerBehavior(...)` calls:

```java
AttunedRegistries.registerBehavior(
	Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "stoneskin"), new StoneskinBehavior());
```

### 7c — Point the Focus at the behavior

**File:** your `data/attuned/attuned/focus/stoneskin_focus.json` from Step 3

Add a `behavior` line:

```json
{
	"item": "attuned:stoneskin_focus",
	"cost": 3,
	"affinity": "bastion",
	"behavior": "attuned:stoneskin"
}
```

The name in `behavior` must match the name you registered in Step 7b.

> **Stat modifiers vs. behavior:** use `modifiers` (Step 3) for anything that is
> just a number — the framework applies and removes it for you. Use a behavior
> for everything else. A Focus can have both.

---

## Checklist

For a stat-only Focus, you created or edited:

- [ ] `AttunedContent.java` — one `register` line, one `output.accept` line
- [ ] `data/attuned/attuned/focus/<name>.json`
- [ ] `assets/attuned/items/<name>.json`
- [ ] `assets/attuned/models/item/<name>.json`
- [ ] `assets/attuned/textures/item/<name>.png`
- [ ] `assets/attuned/lang/en_us.json` — four lines

A power Focus also has:

- [ ] `content/behavior/<Name>Behavior.java`
- [ ] one `registerBehavior` line in `AttunedContent.init()`
- [ ] a `behavior` line in its `.json`

## Test your Focus

Run the game from the project folder:

```
./gradlew runClient
```

Open the creative inventory, find the **Attuned** tab, and your Focus should be
there. Equip it in a Focus slot and check the tooltip and the effect.

If the build fails, the error message names the file and line — usually a typo
or a missing comma. If the item shows a missing-texture pattern, re-check the
name in Step 5.

## Changing an existing Focus

You rarely need code for this:

- **Cost, affinity, or stats** — edit that Focus's file in
  `data/attuned/attuned/focus/`. See [reference.md](reference.md).
- **Name, lore, or effect text** — edit its lines in `en_us.json`.
- **Look** — replace its `.png` in `textures/item/`.

In a running game, the `/reload` command re-reads datapack files, so cost and
affinity changes show up without a restart. Asset and text changes need the game
relaunched.

## Removing a Focus

Reverse the recipe: delete the line in `AttunedContent.java` (and its
`output.accept` line, and any `registerBehavior` line), then delete the Focus's
`.json` files, texture, behavior class, and `en_us.json` lines.
