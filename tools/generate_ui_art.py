import json
import math
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/attuned"
TEXTURES = ASSETS / "textures"
ITEM_DEFINITIONS = ASSETS / "items"
ITEM_MODELS = ASSETS / "models/item"

# The blank, resource-pack-skinnable Focus pool size. Must stay in step with
# AttunedContent.registerCustomFocusPool() and GenericFocusItemContractTest.
CUSTOM_FOCUS_COUNT = 8


STONE_DARK = (22, 20, 28, 255)
STONE_SHADOW = (42, 38, 50, 255)
STONE_MID = (72, 66, 82, 255)
STONE_FACE = (96, 90, 108, 255)
STONE_LIGHT = (139, 132, 152, 255)
AMETHYST = (150, 108, 224, 255)
AMETHYST_DARK = (80, 52, 132, 255)
AMETHYST_LIGHT = (213, 184, 255, 255)
FURY = (224, 74, 54, 255)
BASTION = (226, 178, 72, 255)
ZEPHYR = (76, 202, 224, 255)
INK = (10, 9, 14, 255)
CLEAR = (0, 0, 0, 0)

# Umbral Eclipse Foci — a deep indigo/violet shadow palette shared across the five
# darkness Foci so the set reads as one family. Each Focus tints its corona with an
# accent so they stay distinct in the inventory.
UMBRAL_VOID = (12, 9, 22, 255)
UMBRAL_DARK = (30, 22, 52, 255)
UMBRAL_MID = (58, 42, 96, 255)
UMBRAL_RIM = (92, 70, 150, 255)
UMBRAL_CORONA = (158, 120, 232, 255)
UMBRAL_GLINT = (214, 188, 255, 255)

# The five shipped Umbral Eclipse Foci and the accent that tints each one's corona.
UMBRAL_FOCI = {
    "gloomstride_focus": (120, 176, 240),
    "duskward_focus": (150, 140, 224),
    "shadowmeld_focus": (96, 150, 210),
    "dreadfang_focus": (208, 92, 168),
    "eclipse_focus": (176, 122, 248),
}


def ensure_dirs():
    (TEXTURES / "gui").mkdir(parents=True, exist_ok=True)
    (TEXTURES / "item").mkdir(parents=True, exist_ok=True)
    ITEM_DEFINITIONS.mkdir(parents=True, exist_ok=True)
    ITEM_MODELS.mkdir(parents=True, exist_ok=True)


def rect(draw, box, fill, outline=None):
    draw.rectangle(box, fill=fill, outline=outline)


def bevel(draw, x, y, w, h, face=STONE_FACE, dark=STONE_DARK, light=STONE_LIGHT):
    rect(draw, (x, y, x + w - 1, y + h - 1), dark)
    rect(draw, (x + 1, y + 1, x + w - 1, y + h - 1), light)
    rect(draw, (x + 1, y + 1, x + w - 2, y + h - 2), face)
    draw.line((x + 1, y + h - 2, x + w - 2, y + h - 2), fill=STONE_SHADOW)
    draw.line((x + w - 2, y + 1, x + w - 2, y + h - 2), fill=STONE_SHADOW)


def inset(draw, x, y, w, h, face=(68, 64, 76, 255)):
    rect(draw, (x, y, x + w - 1, y + h - 1), STONE_DARK)
    rect(draw, (x + 1, y + 1, x + w - 1, y + h - 1), STONE_LIGHT)
    rect(draw, (x + 1, y + 1, x + w - 2, y + h - 2), face)
    draw.line((x + 1, y + 1, x + w - 2, y + 1), fill=STONE_SHADOW)
    draw.line((x + 1, y + 1, x + 1, y + h - 2), fill=STONE_SHADOW)


def slot_well(draw, x, y):
    inset(draw, x - 1, y - 1, 18, 18, (106, 101, 116, 255))
    draw.point((x + 1, y + 1), fill=AMETHYST_LIGHT)
    draw.point((x + 14, y + 14), fill=STONE_DARK)


def deterministic_speckles(draw, w, h, color, every=29):
    for y in range(3, h - 3):
        for x in range(3, w - 3):
            if (x * 17 + y * 31) % every == 0:
                draw.point((x, y), fill=color)


def altar():
    img = Image.new("RGBA", (216, 190), CLEAR)
    draw = ImageDraw.Draw(img)
    bevel(draw, 0, 0, 216, 190, (74, 69, 84, 255))
    deterministic_speckles(draw, 216, 190, (84, 78, 94, 255), 47)

    # Ritual header.
    bevel(draw, 7, 17, 202, 74, (58, 52, 70, 255), STONE_DARK, (122, 114, 138, 255))
    rect(draw, (10, 20, 205, 27), (42, 37, 54, 255))
    for x in range(18, 199, 18):
        draw.line((x, 20, x + 4, 24), fill=(70, 56, 104, 255))

    # Stance channel.
    rect(draw, (13, 66, 202, 70), STONE_DARK)
    rect(draw, (14, 67, 76, 68), FURY)
    rect(draw, (77, 67, 139, 68), BASTION)
    rect(draw, (140, 67, 201, 68), ZEPHYR)
    rect(draw, (13, 74, 202, 88), (85, 80, 95, 255))
    draw.line((13, 74, 202, 74), fill=AMETHYST_DARK)
    draw.line((13, 88, 202, 88), fill=STONE_DARK)

    # Shard socket and button cradle.
    inset(draw, 127, 33, 20, 20, (74, 68, 86, 255))
    rect(draw, (130, 36, 143, 49), (55, 48, 72, 255))
    draw.rectangle((131, 37, 142, 48), outline=AMETHYST_DARK)
    bevel(draw, 151, 31, 50, 20, (66, 59, 77, 255))
    rect(draw, (154, 34, 197, 47), (39, 36, 46, 255))
    draw.rectangle((155, 35, 196, 46), outline=(110, 91, 152, 255))

    # Inventory deck.
    rect(draw, (8, 94, 208, 95), STONE_DARK)
    rect(draw, (14, 101, 202, 178), (65, 60, 72, 255))
    draw.rectangle((14, 101, 202, 178), outline=(122, 114, 138, 255))
    for row in range(3):
        for col in range(9):
            slot_well(draw, 24 + col * 18, 101 + row * 18)
    for col in range(9):
        slot_well(draw, 24 + col * 18, 159)

    # Amethyst corner teeth.
    for x, y, sx, sy in ((3, 3, 1, 1), (212, 3, -1, 1), (3, 186, 1, -1), (212, 186, -1, -1)):
        draw.line((x, y, x + sx * 7, y), fill=AMETHYST_LIGHT)
        draw.line((x, y, x, y + sy * 7), fill=AMETHYST_LIGHT)
        draw.point((x + sx * 2, y + sy * 2), fill=AMETHYST)

    img.save(TEXTURES / "gui/altar.png")


def reweaving_well(draw, x, y, w=24, h=24, accent=ZEPHYR):
    inset(draw, x, y, w, h, (31, 33, 40, 255))
    rect(draw, (x + 3, y + 3, x + w - 4, y + h - 4), (13, 15, 20, 255))
    draw.rectangle((x + 4, y + 4, x + w - 5, y + h - 5), outline=(77, 72, 86, 255))
    draw.point((x + 3, y + 3), fill=accent)
    draw.point((x + w - 4, y + h - 4), fill=STONE_DARK)


def reweaving_gui():
    img = Image.new("RGBA", (216, 190), CLEAR)
    draw = ImageDraw.Draw(img)

    panel = (27, 30, 36, 255)
    panel_mid = (41, 43, 51, 255)
    panel_light = (70, 68, 78, 255)
    teal = (48, 188, 190, 255)
    teal_dark = (19, 88, 98, 255)
    violet = (150, 108, 224, 255)

    bevel(draw, 0, 0, 216, 190, panel_mid, (9, 11, 15, 255), panel_light)
    deterministic_speckles(draw, 216, 190, (56, 58, 66, 255), 53)
    rect(draw, (9, 12, 206, 20), (11, 14, 19, 255))
    draw.line((18, 18, 82, 18), fill=teal_dark)
    draw.line((134, 18, 198, 18), fill=teal_dark)
    draw.line((29, 21, 58, 41), fill=teal)
    draw.line((159, 21, 129, 41), fill=teal)

    # Top crest and corner pillars.
    bevel(draw, 3, 3, 18, 31, (37, 40, 48, 255), (8, 9, 12, 255), panel_light)
    bevel(draw, 195, 3, 18, 31, (37, 40, 48, 255), (8, 9, 12, 255), panel_light)
    for cx in (100, 108, 116):
        draw.polygon([(cx, 6), (cx + 6, 12), (cx, 18), (cx - 6, 12)],
            fill=(38, 34, 48, 255), outline=(12, 12, 18, 255))
    draw.line((29, 15, 88, 15), fill=(102, 95, 114, 255))
    draw.line((128, 15, 187, 15), fill=(102, 95, 114, 255))

    # Side runes and lower stone columns.
    for y in range(48, 102, 10):
        draw.line((11, y, 16, y + 5), fill=teal_dark)
        draw.line((16, y + 5, 11, y + 10), fill=teal)
        draw.line((205, y, 200, y + 5), fill=teal_dark)
        draw.line((200, y + 5, 205, y + 10), fill=teal)
    for x in (3, 196):
        bevel(draw, x, 154, 17, 31, (43, 45, 52, 255), (8, 9, 12, 255), (79, 78, 88, 255))

    # Functional wells mirror ReweavingMenu's painted well constants.
    for x in (26, 53, 80):
        reweaving_well(draw, x, 56, 24, 24, teal)
        draw.line((x + 12, 41, x + 12, 54), fill=teal_dark)
        draw.point((x + 12, 39), fill=teal)
    reweaving_well(draw, 107, 56, 24, 24, violet)
    draw.polygon([(119, 38), (126, 49), (121, 61), (116, 61), (111, 49)],
        fill=AMETHYST, outline=AMETHYST_DARK)
    reweaving_well(draw, 151, 47, 26, 26, teal)
    draw.line((137, 59, 147, 59), fill=teal_dark)
    draw.line((144, 55, 149, 59), fill=teal)
    draw.line((144, 63, 149, 59), fill=teal)

    # Reweave button and hint channel.
    bevel(draw, 134, 83, 54, 16, (36, 38, 46, 255), (9, 11, 15, 255), (77, 72, 86, 255))
    rect(draw, (137, 86, 185, 95), (15, 18, 23, 255))
    draw.line((138, 87, 184, 87), fill=teal_dark)
    rect(draw, (14, 84, 127, 97), (25, 27, 34, 255))
    draw.line((15, 85, 126, 85), fill=(61, 57, 72, 255))

    # Player inventory and hotbar.
    rect(draw, (17, 101, 199, 185), (28, 30, 37, 255))
    draw.rectangle((17, 101, 199, 185), outline=(77, 72, 86, 255))
    draw.line((25, 104, 101, 104), fill=teal_dark)
    draw.line((115, 104, 191, 104), fill=teal_dark)
    draw.polygon([(108, 100), (113, 105), (108, 110), (103, 105)], fill=AMETHYST, outline=AMETHYST_DARK)
    for row in range(3):
        for col in range(9):
            slot_well(draw, 27 + col * 18, 108 + row * 18)
    for col in range(9):
        slot_well(draw, 27 + col * 18, 166)

    for x, y, sx, sy in ((5, 5, 1, 1), (210, 5, -1, 1), (5, 184, 1, -1), (210, 184, -1, -1)):
        draw.line((x, y, x + sx * 7, y), fill=teal)
        draw.line((x, y, x, y + sy * 7), fill=teal_dark)
        draw.point((x + sx * 2, y + sy * 2), fill=AMETHYST_LIGHT)

    img.save(TEXTURES / "gui/altar_of_reweaving.png")


def focus_panel():
    img = Image.new("RGBA", (28, 124), CLEAR)
    draw = ImageDraw.Draw(img)
    bevel(draw, 0, 0, 28, 124, (64, 58, 76, 245))
    deterministic_speckles(draw, 28, 124, (83, 76, 96, 220), 19)
    rect(draw, (6, 2, 21, 4), AMETHYST_DARK)
    rect(draw, (8, 3, 19, 3), AMETHYST_LIGHT)
    for i in range(6):
        y = 8 + i * 18
        slot_well(draw, 5, y)
        draw.rectangle((2, y + 4, 3, y + 11), fill=(31, 28, 38, 255))
        draw.point((2, y + 4 + i % 4), fill=AMETHYST_LIGHT)
    rect(draw, (5, 114, 22, 119), STONE_DARK)
    rect(draw, (6, 115, 21, 118), (40, 37, 48, 255))
    img.save(TEXTURES / "gui/focus_panel.png")


def satchel_gui():
    img = Image.new("RGBA", (176, 166), CLEAR)
    draw = ImageDraw.Draw(img)

    leather_dark = (34, 21, 17, 255)
    leather_shadow = (59, 35, 24, 255)
    leather = (104, 59, 35, 255)
    leather_light = (168, 111, 61, 255)
    brass = (214, 158, 68, 255)
    brass_light = (247, 211, 111, 255)
    panel = (33, 31, 38, 255)
    panel_light = (74, 68, 80, 255)

    # Pixel-cleaned from the leather/amethyst satchel concept,
    # while preserving the exact slot coordinates used by SatchelMenu.
    bevel(draw, 0, 0, 176, 166, leather, (12, 8, 7, 255), leather_light)
    rect(draw, (3, 3, 172, 162), leather_shadow)
    deterministic_speckles(draw, 176, 166, (126, 75, 39, 255), 47)
    draw.rectangle((4, 4, 171, 161), outline=(18, 12, 10, 255))

    rect(draw, (7, 3, 168, 17), (42, 25, 20, 255))
    draw.line((9, 16, 166, 16), fill=AMETHYST_DARK)
    for x in range(14, 164, 12):
        draw.line((x, 7, x + 4, 7), fill=brass)
        draw.point((x + 1, 8), fill=brass_light)

    for x, y in ((8, 5), (158, 5), (8, 153), (158, 153)):
        draw.polygon([(x + 5, y), (x + 10, y + 5), (x + 5, y + 10), (x, y + 5)],
            fill=AMETHYST_DARK, outline=(12, 8, 18, 255))
        draw.polygon([(x + 5, y + 2), (x + 8, y + 5), (x + 5, y + 8), (x + 2, y + 5)],
            fill=AMETHYST)
        draw.point((x + 4, y + 4), fill=AMETHYST_LIGHT)

    rect(draw, (7, 17, 169, 73), panel)
    draw.rectangle((7, 17, 169, 73), outline=panel_light)
    draw.line((8, 18, 168, 18), fill=(95, 73, 122, 255))
    for row in range(3):
        for col in range(9):
            slot_well(draw, 8 + col * 18, 18 + row * 18)

    rect(draw, (7, 75, 169, 82), (42, 25, 20, 255))
    draw.line((10, 76, 70, 76), fill=brass)
    draw.line((106, 76, 166, 76), fill=brass)
    draw.line((8, 81, 168, 81), fill=(17, 10, 9, 255))
    draw.polygon([(88, 74), (94, 79), (88, 84), (82, 79)], fill=AMETHYST, outline=AMETHYST_DARK)

    rect(draw, (7, 83, 169, 161), panel)
    draw.rectangle((7, 83, 169, 161), outline=panel_light)
    draw.line((8, 84, 168, 84), fill=(95, 73, 122, 255))
    for row in range(3):
        for col in range(9):
            slot_well(draw, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot_well(draw, 8 + col * 18, 142)

    for y in range(21, 152, 13):
        draw.line((3, y, 3, y + 5), fill=brass)
        draw.line((172, y, 172, y + 5), fill=brass)
    for x in range(20, 154, 18):
        draw.line((x, 162, x + 7, 162), fill=brass)
        draw.point((x + 1, 161), fill=brass_light)

    img.save(TEXTURES / "gui/satchel.png")


def journal_gui():
    img = Image.new("RGBA", (336, 214), CLEAR)
    draw = ImageDraw.Draw(img)

    frame = (31, 27, 40, 255)
    frame_dark = (9, 9, 13, 255)
    frame_light = (87, 78, 101, 255)
    page = (201, 184, 150, 255)
    page_edge = (224, 209, 178, 255)
    page_shadow = (120, 104, 84, 255)
    rail = (20, 17, 28, 255)
    rail_border = (74, 62, 93, 255)
    violet = (150, 108, 224, 255)

    bevel(draw, 0, 0, 336, 214, frame, frame_dark, frame_light)
    deterministic_speckles(draw, 336, 214, (51, 45, 64, 255), 67)

    # Left chapter rail: a clean recessed panel. The screen draws the tab rows,
    # so no tab slots are painted here (they are no longer covered/misaligned).
    rect(draw, (8, 12, 104, 202), rail)
    draw.rectangle((8, 12, 104, 202), outline=rail_border)
    draw.line((10, 14, 10, 200), fill=(40, 33, 54, 255))

    # Spine divider between the rail and the reading page.
    bevel(draw, 104, 5, 8, 204, (49, 36, 55, 255), frame_dark, (111, 82, 125, 255))

    # Reading page: lighter parchment for crisp dark text (matches the mockup).
    rect(draw, (112, 12, 328, 181), page)
    draw.rectangle((112, 12, 328, 181), outline=page_shadow)
    deterministic_speckles(draw, 328, 181, (186, 169, 138, 255), 71)
    draw.line((118, 18, 322, 18), fill=page_edge)
    draw.line((118, 176, 322, 176), fill=(150, 132, 104, 255))

    # Scrollbar gutter near the right edge of the reading page.
    rect(draw, (320, 20, 323, 174), (171, 156, 126, 255))

    # Page navigation button wells.
    for x in (112, 262):
        bevel(draw, x, 182, 66, 20, (41, 36, 50, 255), frame_dark, frame_light)
        rect(draw, (116 if x == 112 else 266, 187, x + 61, 196), (19, 17, 25, 255))
        draw.line((x + 5, 188, x + 60, 188), fill=(95, 81, 122, 255))
    draw.polygon([(126, 192), (136, 186), (136, 198)], fill=violet)
    draw.polygon([(314, 192), (304, 186), (304, 198)], fill=violet)
    draw.polygon([(220, 181), (230, 192), (220, 203), (210, 192)], fill=violet, outline=AMETHYST_DARK)

    # Corner gems.
    for x, y in ((8, 8), (319, 8), (8, 195), (319, 195)):
        draw.polygon([(x + 8, y), (x + 15, y + 8), (x + 8, y + 15), (x + 1, y + 8)],
            fill=AMETHYST_DARK, outline=frame_dark)
        draw.polygon([(x + 8, y + 2), (x + 13, y + 8), (x + 8, y + 13), (x + 3, y + 8)],
            fill=AMETHYST)
        draw.point((x + 7, y + 5), fill=AMETHYST_LIGHT)

    img.save(TEXTURES / "gui/attunement_journal.png")


def hud_backplate():
    img = Image.new("RGBA", (50, 24), CLEAR)
    draw = ImageDraw.Draw(img)
    rect(draw, (2, 3, 47, 20), (18, 16, 24, 210))
    draw.rectangle((2, 3, 47, 20), outline=(108, 96, 132, 230))
    draw.line((3, 4, 46, 4), fill=(176, 150, 228, 180))
    draw.line((3, 20, 46, 20), fill=(4, 4, 8, 180))
    draw.rectangle((5, 2, 26, 23), outline=(37, 33, 46, 230))
    draw.rectangle((31, 5, 47, 20), outline=(37, 33, 46, 210))
    rect(draw, (13, 0, 36, 1), AMETHYST_DARK)
    rect(draw, (19, 22, 30, 23), AMETHYST_LIGHT)
    img.save(TEXTURES / "gui/hud_backplate.png")


def journal():
    img = Image.new("RGBA", (64, 64), CLEAR)
    draw = ImageDraw.Draw(img)
    # The Foci use 64px item art, so the journal keeps the same density instead
    # of scaling a tiny 16px book beside much sharper equipment.
    rect(draw, (19, 6, 48, 58), (16, 12, 22, 255))
    rect(draw, (21, 4, 45, 56), (46, 31, 60, 255))
    rect(draw, (24, 7, 48, 58), (78, 52, 96, 255))
    draw.line((23, 5, 45, 5), fill=(120, 86, 150, 255))
    draw.line((24, 7, 47, 7), fill=(145, 105, 178, 255))
    draw.line((21, 8, 21, 54), fill=(20, 14, 26, 255))
    draw.line((22, 8, 22, 54), fill=(32, 20, 38, 255))
    draw.line((48, 10, 48, 58), fill=(23, 16, 30, 255))
    draw.line((20, 56, 47, 56), fill=(18, 12, 24, 255))
    draw.line((21, 58, 49, 58), fill=(7, 6, 10, 255))

    # Page block and chipped parchment edge.
    rect(draw, (27, 50, 49, 58), (224, 206, 158, 255))
    draw.line((29, 52, 49, 52), fill=(164, 134, 85, 255))
    draw.line((31, 55, 48, 55), fill=(137, 106, 62, 255))
    for x in range(29, 48, 4):
        draw.point((x, 57), fill=(102, 72, 42, 255))

    # Raised cover panel.
    rect(draw, (27, 12, 43, 43), (52, 34, 70, 255))
    draw.rectangle((26, 11, 44, 44), outline=(18, 12, 24, 255))
    draw.line((27, 12, 43, 12), fill=(119, 83, 156, 255))
    draw.line((27, 13, 27, 43), fill=(90, 62, 118, 255))
    draw.line((43, 13, 43, 43), fill=(27, 18, 36, 255))
    draw.line((28, 43, 43, 43), fill=(26, 17, 34, 255))

    # Amethyst sigil with enough pixels to read after item scaling.
    draw.polygon(
        [(35, 15), (41, 23), (38, 35), (31, 35), (28, 23)],
        fill=AMETHYST_DARK,
        outline=(32, 22, 48, 255),
    )
    draw.polygon(
        [(35, 17), (39, 23), (36, 33), (32, 33), (30, 23)],
        fill=AMETHYST,
    )
    draw.polygon([(35, 17), (37, 23), (35, 31), (32, 33), (30, 23)], fill=AMETHYST_LIGHT)
    draw.line((35, 17, 35, 31), fill=(235, 218, 255, 255))
    draw.point((38, 21), fill=(246, 237, 255, 255))
    draw.point((33, 27), fill=(88, 52, 148, 255))

    # Brass binding, clasp, and bookmark.
    rect(draw, (16, 12, 23, 52), (95, 62, 25, 255))
    draw.line((17, 13, 17, 50), fill=(211, 162, 76, 255))
    draw.line((22, 13, 22, 52), fill=(51, 31, 13, 255))
    for y in (16, 25, 34, 43):
        rect(draw, (17, y, 22, y + 3), (190, 139, 56, 255))
        draw.line((18, y + 1, 21, y + 1), fill=(245, 204, 105, 255))
    rect(draw, (43, 29, 54, 37), (93, 58, 20, 255))
    rect(draw, (44, 30, 52, 35), (202, 151, 65, 255))
    draw.line((45, 31, 51, 31), fill=(247, 211, 111, 255))
    rect(draw, (47, 32, 49, 34), AMETHYST_LIGHT)
    rect(draw, (33, 45, 38, 61), (118, 32, 58, 255))
    draw.line((34, 46, 34, 58), fill=(224, 74, 104, 255))
    draw.point((36, 60), fill=(224, 74, 104, 255))

    deterministic_speckles(draw, 64, 64, (112, 77, 140, 255), 83)
    img.save(TEXTURES / "item/attunement_journal.png")


def satchel_item():
    img = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(img)
    leather_dark = (54, 34, 26, 255)
    leather = (112, 70, 43, 255)
    leather_light = (176, 121, 70, 255)
    leather_shadow = (31, 19, 15, 255)
    stitch = (226, 178, 104, 255)

    # Pixel-cleaned from the item source: chunky leather body,
    # brass clasp/stitches, and a readable amethyst foci socket.
    rect(draw, (3, 5, 12, 14), leather_shadow)
    rect(draw, (2, 7, 13, 13), leather_dark)
    rect(draw, (3, 5, 12, 12), leather)
    draw.line((4, 5, 11, 5), fill=leather_light)
    draw.line((3, 12, 12, 12), fill=(42, 25, 19, 255))
    draw.line((2, 8, 2, 12), fill=(22, 14, 12, 255))
    draw.line((13, 8, 13, 12), fill=(22, 14, 12, 255))

    draw.arc((4, 1, 11, 8), 188, 352, fill=leather_light)
    draw.arc((5, 2, 10, 8), 188, 352, fill=leather_dark)
    draw.point((5, 3), fill=(218, 154, 79, 255))
    draw.point((10, 3), fill=(218, 154, 79, 255))

    draw.line((4, 7, 11, 7), fill=(80, 45, 31, 255))
    draw.line((4, 8, 11, 8), fill=AMETHYST_DARK)
    draw.point((5, 8), fill=AMETHYST)
    draw.point((10, 8), fill=AMETHYST)

    draw.polygon([(8, 9), (11, 11), (8, 14), (5, 11)], fill=AMETHYST_DARK, outline=leather_dark)
    draw.polygon([(8, 10), (10, 11), (8, 13), (6, 11)], fill=AMETHYST)
    draw.line((8, 10, 8, 13), fill=AMETHYST_LIGHT)

    rect(draw, (6, 6, 9, 8), (88, 50, 31, 255))
    rect(draw, (7, 6, 8, 7), stitch)
    draw.point((7, 7), fill=(255, 231, 132, 255))
    for x in (4, 6, 10, 12):
        draw.point((x, 11), fill=stitch)
    draw.point((4, 6), fill=(214, 158, 68, 255))
    draw.point((12, 6), fill=(214, 158, 68, 255))
    img.save(TEXTURES / "item/satchel_of_foci.png")


def grand_satchel_item():
    """A palette-shifted variant of satchel_item() for the Grand Focus Reliquary:
    the same chunky leather body redrawn deterministically with a richer gold trim
    so the second tier reads as an upgrade at a glance."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(img)
    # Deeper, warmer leather + a full gold (brass) trim instead of the satchel's
    # muted stitching, so the grand tier is distinct but clearly the same family.
    leather_dark = (44, 28, 18, 255)
    leather = (96, 60, 34, 255)
    leather_light = (158, 108, 58, 255)
    leather_shadow = (26, 16, 11, 255)
    gold = (236, 196, 96, 255)
    gold_light = (255, 232, 150, 255)
    gold_dark = (176, 128, 52, 255)

    rect(draw, (3, 5, 12, 14), leather_shadow)
    rect(draw, (2, 7, 13, 13), leather_dark)
    rect(draw, (3, 5, 12, 12), leather)
    draw.line((4, 5, 11, 5), fill=leather_light)
    draw.line((3, 12, 12, 12), fill=(34, 21, 15, 255))
    draw.line((2, 8, 2, 12), fill=(18, 11, 9, 255))
    draw.line((13, 8, 13, 12), fill=(18, 11, 9, 255))

    # Gold clasp arc instead of the satchel's leather flap.
    draw.arc((4, 1, 11, 8), 188, 352, fill=gold)
    draw.arc((5, 2, 10, 8), 188, 352, fill=gold_dark)
    draw.point((5, 3), fill=gold_light)
    draw.point((10, 3), fill=gold_light)

    draw.line((4, 7, 11, 7), fill=gold_dark)
    draw.line((4, 8, 11, 8), fill=AMETHYST_DARK)
    draw.point((5, 8), fill=AMETHYST)
    draw.point((10, 8), fill=AMETHYST)

    # A larger amethyst foci socket, ringed in gold.
    draw.polygon([(8, 9), (11, 11), (8, 14), (5, 11)], fill=AMETHYST_DARK, outline=gold_dark)
    draw.polygon([(8, 10), (10, 11), (8, 13), (6, 11)], fill=AMETHYST)
    draw.line((8, 10, 8, 13), fill=AMETHYST_LIGHT)

    rect(draw, (6, 6, 9, 8), (80, 46, 28, 255))
    rect(draw, (7, 6, 8, 7), gold)
    draw.point((7, 7), fill=gold_light)
    # Gold stud trim along the body seam and corners.
    for x in (4, 6, 10, 12):
        draw.point((x, 11), fill=gold)
    draw.point((4, 6), fill=gold)
    draw.point((12, 6), fill=gold)
    draw.point((3, 13), fill=gold_dark)
    draw.point((12, 13), fill=gold_dark)
    img.save(TEXTURES / "item/grand_satchel_of_foci.png")


def _custom_focus_frame(accent, accent_light, accent_dark, rim):
    """Draws one 16x16 blank Focus medallion in a single accent palette.

    A neutral, deliberately featureless talisman so a resource pack can repaint or
    replace it; the per-item hue keeps the eight defaults visually distinct in the
    creative tab without implying any built-in identity.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(img)

    # Stone medallion body with a chunky bevel, matching the Focus art density.
    bevel(draw, 2, 2, 12, 12, STONE_FACE, STONE_DARK, STONE_LIGHT)
    deterministic_speckles(draw, 16, 16, STONE_SHADOW, 23)

    # Recessed gem well, then a faceted accent gem in the centre.
    inset(draw, 4, 4, 8, 8, (60, 56, 68, 255))
    draw.polygon([(8, 4), (12, 8), (8, 12), (4, 8)], fill=accent_dark, outline=INK)
    draw.polygon([(8, 5), (11, 8), (8, 11), (5, 8)], fill=accent)
    draw.line((8, 5, 8, 11), fill=accent_light)
    draw.point((7, 7), fill=accent_light)
    draw.point((9, 9), fill=accent_dark)

    # Accent rim sparks at the cardinal points so the hue reads on the frame too.
    for x, y in ((8, 1), (8, 14), (1, 8), (14, 8)):
        draw.point((x, y), fill=rim)
    return img


def _lerp(a, b, t):
    """Linear blend between two RGB(A) tuples."""
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def _rgba(rgb, alpha=255):
    """Return an RGB tuple as an opaque/transparent RGBA tuple."""
    return (rgb[0], rgb[1], rgb[2], alpha)


def _pulse(phase, offset=0.0):
    return (math.sin((phase + offset) * math.tau) + 1.0) / 2.0


def _closed_line(draw, points, fill, width=1):
    draw.line(points + [points[0]], fill=fill, width=width)


def _draw_umbral_base(draw, accent, phase):
    """Shared Attuned-style medallion base for the Umbral Eclipse family."""
    pulse = _pulse(phase)
    accent_rgba = _rgba(accent)
    rim = _lerp(UMBRAL_CORONA, accent_rgba, 0.34 + pulse * 0.18)
    rim_hot = _lerp(UMBRAL_GLINT, accent_rgba, 0.42 + pulse * 0.10)
    face = _lerp(UMBRAL_MID, accent_rgba, 0.14 + pulse * 0.08)

    # Keep the same chunky, centered medallion language as the shipped Foci:
    # heavy black outline, metal-like bevel, recessed colored field, tiny rim glints.
    shadow = [(32, 4), (48, 8), (58, 18), (62, 33), (58, 48), (47, 59), (31, 63), (16, 58), (5, 47), (1, 31), (6, 16), (17, 7)]
    outer = [(32, 2), (48, 6), (59, 17), (62, 32), (58, 47), (47, 58), (32, 62), (17, 58), (6, 47), (2, 32), (6, 17), (17, 6)]
    bevel_poly = [(32, 5), (46, 9), (55, 18), (58, 32), (54, 45), (45, 55), (32, 59), (19, 55), (9, 45), (6, 32), (9, 19), (19, 9)]
    field = [(32, 11), (43, 14), (51, 23), (53, 32), (50, 42), (42, 51), (32, 54), (22, 51), (14, 42), (11, 32), (13, 23), (22, 14)]

    draw.polygon(shadow, fill=(3, 3, 7, 160))
    draw.polygon(outer, fill=INK)
    draw.polygon(bevel_poly, fill=UMBRAL_VOID)
    _closed_line(draw, bevel_poly, INK, 2)
    draw.line((18, 9, 32, 5, 46, 9, 55, 18), fill=_lerp(UMBRAL_GLINT, accent_rgba, 0.22), width=3)
    draw.line((58, 31, 54, 45, 45, 55, 32, 59, 19, 55), fill=(5, 4, 9, 255), width=3)
    draw.polygon(field, fill=face)
    _closed_line(draw, field, rim, 3)

    # Cardinal studs mirror the small gem/rivet language on many existing Foci,
    # but the orbiting bright stud also guarantees each animation frame differs.
    for x, y in ((32, 8), (56, 32), (32, 56), (8, 32)):
        draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=UMBRAL_DARK, outline=INK)
        draw.point((x - 1, y - 1), fill=rim_hot)
    angle = phase * math.tau
    sx = 32 + math.cos(angle) * 24
    sy = 32 + math.sin(angle) * 24
    draw.ellipse((sx - 2, sy - 2, sx + 2, sy + 2), fill=_lerp(UMBRAL_GLINT, accent_rgba, 0.35))


def _draw_gloomstride(draw, accent, phase):
    """Chunky boot glyph on the shared medallion."""
    pulse = _pulse(phase, 0.10)
    cyan = _rgba(accent)
    light = _lerp(UMBRAL_GLINT, cyan, 0.45)
    mid = _lerp(UMBRAL_CORONA, cyan, 0.68)
    dark = _lerp(UMBRAL_DARK, cyan, 0.20)
    shift = round((pulse - 0.5) * 2)

    # Two compact wind cuts behind the boot; no long neon arcs outside the frame.
    draw.line((15 + shift, 25, 27 + shift, 20), fill=_lerp(UMBRAL_RIM, cyan, 0.70), width=3)
    draw.line((13 + shift, 34, 27 + shift, 31), fill=_lerp(UMBRAL_DARK, cyan, 0.78), width=3)

    boot_shadow = [(22, 18), (38, 18), (41, 35), (51, 38), (54, 45), (48, 52), (20, 52), (14, 45), (21, 39), (28, 39), (27, 29), (22, 27)]
    boot = [(24, 21), (36, 21), (38, 36), (48, 40), (50, 44), (45, 48), (22, 48), (18, 45), (23, 41), (30, 41), (29, 30), (24, 28)]
    draw.polygon(boot_shadow, fill=INK)
    draw.polygon(boot, fill=dark)
    draw.line((24, 22, 36, 22, 38, 36, 48, 40), fill=light, width=3)
    draw.line((22, 47, 45, 47), fill=mid, width=4)
    draw.line((24, 29, 29, 31, 30, 41), fill=mid, width=3)
    draw.point((38, 38), fill=light)


def _draw_duskward(draw, accent, phase):
    """Shield glyph contained inside the shared medallion."""
    pulse = _pulse(phase, 0.24)
    accent_rgba = _rgba(accent)
    light = _lerp(UMBRAL_GLINT, accent_rgba, 0.36)
    rim = _lerp(UMBRAL_CORONA, accent_rgba, 0.50 + pulse * 0.14)
    dark = _lerp(UMBRAL_DARK, accent_rgba, 0.22)

    shield_shadow = [(32, 14), (47, 21), (44, 41), (32, 54), (20, 41), (17, 21)]
    shield = [(32, 17), (44, 22), (41, 39), (32, 50), (23, 39), (20, 22)]
    inner = [(32, 22), (39, 26), (37, 36), (32, 44), (27, 36), (25, 26)]
    draw.polygon(shield_shadow, fill=INK)
    draw.polygon(shield, fill=dark)
    _closed_line(draw, shield, rim, 3)
    draw.polygon(inner, fill=UMBRAL_DARK)
    draw.line((23, 23, 32, 18, 42, 23), fill=light, width=3)
    draw.line((42, 24, 39, 39, 32, 49), fill=(5, 4, 9, 255), width=3)
    draw.line((32, 23, 32, 44), fill=_lerp(UMBRAL_GLINT, accent_rgba, 0.48), width=3)

    # Small dusk crescent reads as protection without turning into a full portrait.
    draw.ellipse((25, 28, 37, 41), fill=light)
    draw.ellipse((31, 26, 42, 42), fill=UMBRAL_DARK)


def _draw_shadowmeld(draw, accent, phase):
    """Hood/mask glyph, reduced to a bold inventory-scale silhouette."""
    pulse = _pulse(phase, 0.42)
    accent_rgba = _rgba(accent)
    rim = _lerp(UMBRAL_CORONA, accent_rgba, 0.52 + pulse * 0.12)
    light = _lerp(UMBRAL_GLINT, accent_rgba, 0.42)
    mist = _lerp(UMBRAL_CORONA, accent_rgba, 0.52)

    # Short mist bars stay inside the medallion and echo existing utility-Foci swirls.
    offset = round((pulse - 0.5) * 2)
    draw.line((18 + offset, 45, 30 + offset, 45), fill=mist, width=3)
    draw.line((34 - offset, 46, 48 - offset, 46), fill=_lerp(UMBRAL_DARK, accent_rgba, 0.78), width=3)

    hood_shadow = [(32, 13), (45, 20), (47, 34), (40, 49), (24, 49), (17, 34), (19, 21)]
    hood = [(32, 16), (42, 22), (44, 33), (38, 45), (26, 45), (20, 33), (22, 23)]
    face = [(32, 23), (39, 29), (37, 38), (32, 43), (27, 38), (25, 29)]
    shoulders = [(24, 40), (40, 40), (49, 48), (43, 53), (21, 53), (15, 48)]
    draw.polygon(hood_shadow, fill=INK)
    draw.polygon(shoulders, fill=INK)
    draw.polygon(hood, fill=_lerp(UMBRAL_DARK, accent_rgba, 0.24))
    _closed_line(draw, hood, rim, 3)
    draw.polygon(face, fill=(4, 4, 8, 255))
    draw.line((24, 24, 32, 17, 41, 23), fill=light, width=3)
    draw.line((21, 33, 27, 44, 37, 44, 43, 33), fill=_lerp(UMBRAL_RIM, accent_rgba, 0.70), width=2)
    draw.line((28, 31, 36, 31), fill=light, width=3)
    draw.point((38, 32), fill=light)


def _draw_dreadfang(draw, accent, phase):
    """Single fang glyph; bright motif, but framed like the other Foci."""
    pulse = _pulse(phase, 0.64)
    magenta = _rgba(accent)
    hot = _lerp((238, 184, 224, 255), magenta, 0.45)
    bone = _lerp((214, 204, 224, 255), magenta, 0.10)
    shade = _lerp((91, 40, 83, 255), magenta, 0.42)

    # Compact maw and sparks are tucked behind the tooth so the silhouette stays simple.
    draw.arc((17, 21, 47, 49), 205, 335, fill=_lerp(UMBRAL_CORONA, magenta, 0.54 + pulse * 0.10), width=3)
    draw.polygon([(20, 31), (27, 27), (32, 31), (37, 27), (45, 31), (38, 36), (31, 34), (24, 36)], fill=_lerp(UMBRAL_VOID, magenta, 0.28), outline=INK)

    fang_shadow = [(32, 14), (41, 25), (37, 45), (31, 54), (25, 45), (23, 25)]
    fang = [(32, 17), (38, 26), (35, 44), (31, 50), (27, 43), (26, 26)]
    draw.polygon(fang_shadow, fill=INK)
    draw.polygon(fang, fill=bone)
    draw.polygon([(32, 18), (35, 27), (32, 43), (28, 42), (27, 27)], fill=(236, 224, 244, 255))
    draw.line((38, 27, 35, 44, 31, 50), fill=shade, width=2)
    draw.line((29, 21, 36, 24), fill=hot, width=1)
    for x, y in ((22, 25), (43, 40)):
        draw.line((x - 2, y, x + 2, y), fill=hot, width=1)
        draw.line((x, y - 2, x, y + 2), fill=hot, width=1)


def _draw_eclipse(draw, accent, phase):
    """Total eclipse glyph built from two bold discs, no noisy sunburst."""
    pulse = _pulse(phase, 0.82)
    violet = _rgba(accent)
    corona = _lerp(UMBRAL_CORONA, violet, 0.44 + pulse * 0.14)
    hot = _lerp((224, 112, 184, 255), violet, 0.48)

    draw.ellipse((18, 18, 46, 46), fill=INK)
    draw.ellipse((20, 20, 44, 44), fill=corona)
    draw.ellipse((24, 24, 40, 40), fill=UMBRAL_VOID, outline=INK)
    draw.arc((20, 20, 44, 44), 210, 330, fill=UMBRAL_GLINT, width=2)
    draw.arc((18, 18, 46, 46), 25, 135, fill=hot, width=2)
    # Four blocky rays imply eclipse power while staying closer to existing Focus icons.
    for x1, y1, x2, y2 in ((32, 14, 32, 19), (32, 45, 32, 50), (14, 32, 19, 32), (45, 32, 50, 32)):
        draw.line((x1, y1, x2, y2), fill=hot, width=2)


def _umbral_focus_frame(name, accent, phase):
    """Draw one 64x64 Umbral Eclipse Focus frame.

    The first pass used one shared eclipse medallion for all five Foci. The current pass
    keeps the shared shadow-violet family palette, but gives each Focus a silhouette
    matched to the private source silhouettes: boot, shield, crouched hood,
    fang, and total eclipse. Geometry remains deterministic so `generate_ui_art.py`
    can rebuild the shipped 64x512 animated sheets at any time.
    """
    img = Image.new("RGBA", (64, 64), CLEAR)
    draw = ImageDraw.Draw(img)
    _draw_umbral_base(draw, accent, phase)

    if name == "gloomstride_focus":
        _draw_gloomstride(draw, accent, phase)
    elif name == "duskward_focus":
        _draw_duskward(draw, accent, phase)
    elif name == "shadowmeld_focus":
        _draw_shadowmeld(draw, accent, phase)
    elif name == "dreadfang_focus":
        _draw_dreadfang(draw, accent, phase)
    elif name == "eclipse_focus":
        _draw_eclipse(draw, accent, phase)
    else:
        draw.polygon([(32, 17), (47, 32), (32, 47), (17, 32)], fill=_rgba(accent), outline=INK)

    return img


def generate_umbral_focus_textures():
    """Regenerate Umbral Eclipse Foci from the private source sheet."""
    from generate_umbral_eclipse_focus_assets import generate_assets

    generate_assets()


ASPECT_PREVIEW_ASSETS = ROOT / "build/asset-previews/aspect-counter-foci"
ASPECT_PREVIEW_TEXTURES = ASPECT_PREVIEW_ASSETS / "textures/item"
ASPECT_PREVIEW_PREVIEWS = ASPECT_PREVIEW_ASSETS / "previews"

ASPECT_PALETTES = {
    "tide": {
        "void": (6, 18, 30, 255),
        "dark": (9, 48, 64, 255),
        "mid": (28, 126, 154, 255),
        "rim": (72, 216, 226, 255),
        "glint": (202, 255, 248, 255),
        "accent": (92, 190, 220, 255),
    },
    "forge": {
        "void": (24, 11, 8, 255),
        "dark": (64, 30, 16, 255),
        "mid": (148, 78, 28, 255),
        "rim": (232, 150, 50, 255),
        "glint": (255, 226, 132, 255),
        "accent": (224, 86, 38, 255),
    },
    "verdant": {
        "void": (10, 22, 12, 255),
        "dark": (28, 58, 22, 255),
        "mid": (70, 132, 48, 255),
        "rim": (118, 206, 72, 255),
        "glint": (220, 255, 166, 255),
        "accent": (90, 180, 68, 255),
    },
    "umbral": {
        "void": (12, 8, 22, 255),
        "dark": (30, 22, 52, 255),
        "mid": (58, 42, 96, 255),
        "rim": (154, 118, 232, 255),
        "glint": (220, 194, 255, 255),
        "accent": (146, 96, 224, 255),
    },
}

ASPECT_PREVIEW_FOCI = {
    "undertow_focus": ("tide", "spiral pull"),
    "riptide_heart_focus": ("tide", "wave heart"),
    "pearlguard_focus": ("tide", "pearl shield"),
    "slagbrand_focus": ("forge", "heated brand"),
    "anvilheart_focus": ("forge", "anvil heart"),
    "sparkweld_focus": ("forge", "weld spark"),
    "thornwake_focus": ("verdant", "thorn ring"),
    "seedcall_focus": ("verdant", "sprouting seed"),
    "bramblegate_focus": ("verdant", "bramble gate"),
    "nullveil_focus": ("umbral", "void veil"),
    "cinderthief_focus": ("umbral", "stolen ember"),
    "snaremoon_focus": ("umbral", "moon snare"),
}


def _draw_aspect_medallion(draw, palette, phase):
    """Shared Attuned Focus base for preview Aspect Foci.

    The preview sprites intentionally use the same family rules as shipped Foci:
    a chunky centered talisman, dark outer outline, beveled rim, recessed field,
    disciplined aspect palette, and only one readable central glyph.
    """
    pulse = _pulse(phase)
    rim = _lerp(palette["rim"], palette["glint"], 0.18 + pulse * 0.16)
    face = _lerp(palette["mid"], palette["accent"], 0.08 + pulse * 0.08)
    hot = _lerp(palette["glint"], palette["accent"], 0.16)

    shadow = [(33, 4), (49, 8), (59, 18), (63, 33), (59, 49), (48, 60), (32, 63), (16, 59), (5, 48), (1, 32), (5, 17), (17, 7)]
    outer = [(32, 2), (48, 6), (59, 17), (62, 32), (58, 47), (47, 58), (32, 62), (17, 58), (6, 47), (2, 32), (6, 17), (17, 6)]
    bevel_poly = [(32, 5), (46, 9), (55, 18), (58, 32), (54, 45), (45, 55), (32, 59), (19, 55), (9, 45), (6, 32), (9, 19), (19, 9)]
    field = [(32, 11), (43, 14), (51, 23), (53, 32), (50, 42), (42, 51), (32, 54), (22, 51), (14, 42), (11, 32), (13, 23), (22, 14)]

    draw.polygon(shadow, fill=(2, 2, 5, 150))
    draw.polygon(outer, fill=INK)
    draw.polygon(bevel_poly, fill=palette["void"])
    _closed_line(draw, bevel_poly, INK, 2)
    draw.line((18, 9, 32, 5, 46, 9, 55, 18), fill=hot, width=3)
    draw.line((58, 31, 54, 45, 45, 55, 32, 59, 19, 55), fill=(4, 4, 8, 255), width=3)
    draw.polygon(field, fill=face)
    _closed_line(draw, field, rim, 3)

    for x, y in ((32, 8), (56, 32), (32, 56), (8, 32)):
        draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=palette["dark"], outline=INK)
        draw.point((x - 1, y - 1), fill=hot)
    angle = phase * math.tau
    sx = 32 + math.cos(angle) * 24
    sy = 32 + math.sin(angle) * 24
    draw.ellipse((sx - 2, sy - 2, sx + 2, sy + 2), fill=hot)


def _draw_undertow(draw, palette, phase):
    pulse = _pulse(phase, 0.08)
    hot = _lerp(palette["glint"], palette["rim"], 0.30)
    dark = palette["void"]
    shift = round((pulse - 0.5) * 2)
    draw.arc((16 + shift, 15, 48 + shift, 47), 35, 330, fill=INK, width=6)
    draw.arc((17 + shift, 16, 47 + shift, 46), 35, 330, fill=palette["rim"], width=4)
    draw.arc((23 - shift, 22, 41 - shift, 40), 220, 70, fill=hot, width=4)
    draw.ellipse((27, 27, 37, 37), fill=dark, outline=INK)
    draw.polygon([(44 + shift, 25), (51 + shift, 30), (43 + shift, 34)], fill=hot, outline=INK)


def _draw_riptide_heart(draw, palette, phase):
    pulse = _pulse(phase, 0.20)
    hot = _lerp(palette["glint"], palette["rim"], 0.22)
    mid = _lerp(palette["rim"], palette["accent"], 0.35)
    bob = round((pulse - 0.5) * 2)
    draw.line((16, 39 + bob, 26, 35 + bob, 38, 39 + bob, 48, 34 + bob), fill=INK, width=5)
    draw.line((16, 39 + bob, 26, 35 + bob, 38, 39 + bob, 48, 34 + bob), fill=mid, width=3)
    heart_shadow = [(32, 48), (20, 36), (18, 26), (25, 20), (32, 25), (39, 20), (46, 26), (44, 36)]
    heart = [(32, 45), (23, 36), (21, 28), (26, 24), (32, 29), (38, 24), (43, 28), (41, 36)]
    draw.polygon(heart_shadow, fill=INK)
    draw.polygon(heart, fill=palette["dark"])
    draw.line((23, 29, 27, 24, 32, 29, 37, 24, 42, 29), fill=hot, width=3)
    draw.line((41, 34, 32, 44, 23, 35), fill=mid, width=3)


def _draw_pearlguard(draw, palette, phase):
    pulse = _pulse(phase, 0.34)
    rim = _lerp(palette["rim"], palette["glint"], 0.22 + pulse * 0.10)
    shield_shadow = [(32, 14), (47, 21), (44, 41), (32, 54), (20, 41), (17, 21)]
    shield = [(32, 17), (44, 22), (41, 39), (32, 50), (23, 39), (20, 22)]
    draw.polygon(shield_shadow, fill=INK)
    draw.polygon(shield, fill=palette["dark"])
    _closed_line(draw, shield, rim, 3)
    draw.line((23, 23, 32, 18, 42, 23), fill=palette["glint"], width=3)
    draw.ellipse((26, 27, 38, 39), fill=INK)
    draw.ellipse((28, 26, 40, 38), fill=(224, 248, 238, 255), outline=rim)
    draw.point((31, 29), fill=(255, 255, 255, 255))


def _draw_slagbrand(draw, palette, phase):
    pulse = _pulse(phase, 0.12)
    hot = _lerp(palette["glint"], palette["accent"], 0.30 + pulse * 0.18)
    blade = [(23, 45), (39, 14), (46, 17), (30, 49)]
    core = [(27, 43), (40, 18), (43, 20), (30, 46)]
    draw.polygon(blade, fill=INK)
    draw.polygon(core, fill=palette["dark"])
    draw.line((40, 18, 30, 46), fill=hot, width=4)
    draw.line((27, 42, 20, 50), fill=palette["rim"], width=4)
    draw.arc((15, 20, 35, 43), 260, 80, fill=hot, width=3)
    draw.polygon([(23, 21), (30, 28), (24, 33), (27, 38), (18, 32)], fill=palette["accent"], outline=INK)


def _draw_anvilheart(draw, palette, phase):
    pulse = _pulse(phase, 0.24)
    hot = _lerp(palette["glint"], palette["rim"], 0.32 + pulse * 0.12)
    ember = _lerp((255, 96, 48, 255), palette["glint"], 0.18 + pulse * 0.08)
    # Chunky anvil silhouette; the first pass read as thin bars at 16px.
    anvil_shadow = [(17, 25), (47, 25), (53, 31), (42, 35), (39, 43), (48, 48), (45, 53), (19, 53), (16, 48), (25, 43), (22, 35), (11, 31)]
    anvil = [(20, 28), (44, 28), (49, 31), (39, 34), (37, 42), (44, 46), (41, 49), (23, 49), (20, 46), (27, 42), (25, 34), (15, 31)]
    draw.polygon(anvil_shadow, fill=INK)
    draw.polygon(anvil, fill=palette["dark"])
    draw.line((20, 29, 44, 29, 49, 31), fill=hot, width=4)
    draw.line((23, 48, 41, 48), fill=palette["rim"], width=3)
    draw.rectangle((24, 18, 40, 28), fill=INK)
    draw.rectangle((27, 20, 37, 28), fill=palette["mid"])
    heart_shadow = [(32, 47), (24, 39), (24, 34), (29, 31), (32, 34), (35, 31), (40, 34), (40, 39)]
    heart = [(32, 44), (27, 39), (27, 35), (30, 34), (32, 36), (34, 34), (37, 35), (37, 39)]
    draw.polygon(heart_shadow, fill=INK)
    draw.polygon(heart, fill=ember)
    draw.point((30, 36), fill=(255, 238, 130, 255))


def _draw_sparkweld(draw, palette, phase):
    pulse = _pulse(phase, 0.38)
    hot = _lerp(palette["glint"], palette["accent"], 0.22 + pulse * 0.22)
    draw.line((18, 46, 46, 18), fill=INK, width=7)
    draw.line((18, 46, 46, 18), fill=palette["dark"], width=5)
    draw.line((22, 42, 42, 22), fill=hot, width=2)
    bolt = [(31, 14), (41, 28), (35, 28), (43, 48), (27, 31), (34, 31)]
    draw.polygon([(x, y + 1) for x, y in bolt], fill=INK)
    draw.polygon(bolt, fill=palette["glint"], outline=palette["rim"])
    for x, y in ((20, 24), (46, 35), (25, 52), (51, 16)):
        draw.line((x - 3, y, x + 3, y), fill=hot, width=2)
        draw.line((x, y - 3, x, y + 3), fill=hot, width=2)


def _draw_thornwake(draw, palette, phase):
    pulse = _pulse(phase, 0.10)
    hot = _lerp(palette["glint"], palette["rim"], 0.30)
    draw.arc((16, 16, 48, 48), 15, 340, fill=INK, width=6)
    draw.arc((17, 17, 47, 47), 15, 340, fill=palette["dark"], width=4)
    draw.arc((20, 20, 44, 44), 205, 95, fill=hot, width=3)
    spikes = [(32, 12), (37, 21), (49, 21), (41, 29), (47, 42), (35, 38), (28, 52), (25, 39), (13, 36), (23, 29), (16, 17), (28, 22)]
    for x, y in spikes[::2]:
        draw.polygon([(x, y), (x + 3, y + 6), (x - 3, y + 6)], fill=palette["rim"], outline=INK)
    draw.ellipse((26, 26, 38, 38), fill=palette["mid"], outline=INK)


def _draw_seedcall(draw, palette, phase):
    pulse = _pulse(phase, 0.27)
    hot = _lerp(palette["glint"], palette["rim"], 0.24)
    seed_shadow = [(32, 20), (43, 31), (39, 47), (31, 54), (23, 47), (21, 31)]
    seed = [(32, 23), (40, 32), (37, 45), (31, 50), (25, 45), (24, 32)]
    draw.polygon(seed_shadow, fill=INK)
    draw.polygon(seed, fill=palette["dark"])
    draw.line((29, 25, 25, 33, 31, 49), fill=hot, width=3)
    draw.line((32, 25, 32, 14), fill=INK, width=5)
    draw.line((32, 25, 32, 15), fill=palette["rim"], width=3)
    draw.ellipse((20, 13, 33, 24), fill=palette["mid"], outline=INK)
    draw.ellipse((31, 12, 45, 23), fill=palette["mid"], outline=INK)
    draw.point((27, 16), fill=palette["glint"])


def _draw_bramblegate(draw, palette, phase):
    pulse = _pulse(phase, 0.43)
    hot = _lerp(palette["glint"], palette["rim"], 0.28 + pulse * 0.12)
    gate_shadow = [(20, 50), (20, 30), (25, 19), (32, 15), (39, 19), (44, 30), (44, 50), (37, 50), (37, 32), (32, 27), (27, 32), (27, 50)]
    gate = [(23, 48), (23, 31), (27, 23), (32, 20), (37, 23), (41, 31), (41, 48), (36, 48), (36, 33), (32, 29), (28, 33), (28, 48)]
    draw.polygon(gate_shadow, fill=INK)
    draw.polygon(gate, fill=palette["dark"])
    draw.line((24, 32, 28, 23, 32, 20, 37, 24, 40, 32), fill=hot, width=3)
    for x in (22, 42):
        draw.line((x, 23, x + (5 if x == 22 else -5), 48), fill=palette["rim"], width=3)
        for y in (29, 38, 45):
            draw.polygon([(x, y), (x + (5 if x == 22 else -5), y + 2), (x, y + 5)], fill=hot, outline=INK)


def _draw_nullveil(draw, palette, phase):
    pulse = _pulse(phase, 0.16)
    hot = _lerp(palette["glint"], palette["rim"], 0.26)
    veil_shadow = [(32, 13), (45, 21), (48, 36), (40, 51), (24, 51), (16, 36), (19, 21)]
    veil = [(32, 16), (42, 23), (44, 35), (38, 47), (26, 47), (20, 35), (22, 23)]
    draw.polygon(veil_shadow, fill=INK)
    draw.polygon(veil, fill=palette["dark"])
    _closed_line(draw, veil, palette["rim"], 3)
    draw.ellipse((23, 25, 41, 41), fill=INK)
    draw.ellipse((26, 27, 38, 39), fill=palette["void"], outline=hot)
    draw.line((32, 20, 32, 48), fill=_lerp(palette["rim"], palette["void"], 0.35), width=3)
    offset = round((pulse - 0.5) * 2)
    draw.line((18 + offset, 43, 30 + offset, 45), fill=palette["rim"], width=2)
    draw.line((34 - offset, 45, 47 - offset, 43), fill=palette["rim"], width=2)


def _draw_cinderthief(draw, palette, phase):
    pulse = _pulse(phase, 0.31)
    ember = _lerp((255, 112, 48, 255), palette["glint"], pulse * 0.20)
    hot = (255, 228, 128, 255)
    # Big stolen ember in the middle; claw silhouette wraps it so it reads at 16px.
    ember_shadow = [(35, 18), (49, 29), (43, 45), (27, 40), (24, 28)]
    ember_poly = [(35, 21), (46, 30), (41, 41), (29, 38), (27, 29)]
    draw.polygon(ember_shadow, fill=INK)
    draw.polygon(ember_poly, fill=ember, outline=INK)
    draw.point((36, 25), fill=hot)

    claw_shadow = [(17, 45), (22, 31), (27, 22), (31, 30), (36, 20), (40, 29), (45, 25), (44, 36), (36, 50), (25, 52)]
    claw = [(20, 43), (24, 32), (27, 27), (31, 35), (35, 25), (38, 34), (42, 31), (41, 36), (34, 47), (27, 49)]
    draw.polygon(claw_shadow, fill=INK)
    draw.polygon(claw, fill=palette["dark"])
    draw.line((24, 33, 28, 48, 35, 46, 41, 35), fill=palette["rim"], width=3)
    draw.line((30, 35, 35, 26, 38, 34), fill=_lerp(palette["glint"], palette["rim"], 0.34), width=2)


def _draw_snaremoon(draw, palette, phase):
    pulse = _pulse(phase, 0.51)
    hot = _lerp(palette["glint"], palette["rim"], 0.24 + pulse * 0.12)
    draw.ellipse((18, 15, 46, 43), fill=INK)
    draw.ellipse((20, 16, 44, 40), fill=hot)
    draw.ellipse((27, 13, 51, 39), fill=palette["void"])
    draw.arc((18, 26, 46, 54), 20, 340, fill=INK, width=5)
    draw.arc((19, 27, 45, 53), 20, 340, fill=palette["rim"], width=3)
    for x, y in ((24, 48), (32, 53), (42, 47)):
        draw.line((x - 3, y - 3, x + 3, y + 3), fill=hot, width=2)
        draw.line((x + 3, y - 3, x - 3, y + 3), fill=hot, width=2)


def _aspect_preview_focus_frame(name, phase):
    aspect, _description = ASPECT_PREVIEW_FOCI[name]
    palette = ASPECT_PALETTES[aspect]
    img = Image.new("RGBA", (64, 64), CLEAR)
    draw = ImageDraw.Draw(img)
    _draw_aspect_medallion(draw, palette, phase)
    if name == "undertow_focus":
        _draw_undertow(draw, palette, phase)
    elif name == "riptide_heart_focus":
        _draw_riptide_heart(draw, palette, phase)
    elif name == "pearlguard_focus":
        _draw_pearlguard(draw, palette, phase)
    elif name == "slagbrand_focus":
        _draw_slagbrand(draw, palette, phase)
    elif name == "anvilheart_focus":
        _draw_anvilheart(draw, palette, phase)
    elif name == "sparkweld_focus":
        _draw_sparkweld(draw, palette, phase)
    elif name == "thornwake_focus":
        _draw_thornwake(draw, palette, phase)
    elif name == "seedcall_focus":
        _draw_seedcall(draw, palette, phase)
    elif name == "bramblegate_focus":
        _draw_bramblegate(draw, palette, phase)
    elif name == "nullveil_focus":
        _draw_nullveil(draw, palette, phase)
    elif name == "cinderthief_focus":
        _draw_cinderthief(draw, palette, phase)
    elif name == "snaremoon_focus":
        _draw_snaremoon(draw, palette, phase)
    else:
        draw.polygon([(32, 17), (47, 32), (32, 47), (17, 32)], fill=palette["rim"], outline=INK)
    return img


def generate_aspect_focus_preview_textures(output_dir=None):
    """Generate 64x512 animated sheets for the Aspect Counter Foci.

    Historical deterministic preview generator only. Do not use this as the final
    art source for the new Aspect Foci. Final shipped Aspect textures should be
    imported from the ignored private source directory instead.
    """

    output = Path(output_dir) if output_dir is not None else ASPECT_PREVIEW_TEXTURES
    output.mkdir(parents=True, exist_ok=True)
    frames = 8
    mcmeta = {"animation": {"frametime": 2, "interpolate": True}}
    for name in ASPECT_PREVIEW_FOCI:
        sheet = Image.new("RGBA", (64, 64 * frames), CLEAR)
        for frame in range(frames):
            phase = frame / frames
            sheet.alpha_composite(_aspect_preview_focus_frame(name, phase), (0, frame * 64))
        sheet.save(output / f"{name}.png")
        (output / f"{name}.png.mcmeta").write_text(json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8")
    return output


def generate_aspect_focus_textures():
    """Deprecated: Aspect Foci are private source imports, not code-drawn."""
    raise RuntimeError(
        "Aspect Focus textures are private source imports. Use .attuned-art-sources "
        "and the import workflow instead of regenerating code-drawn art."
    )



def generate_custom_focus_textures():
    """Regenerate the custom Focus pool from the private source sheet."""
    from generate_custom_focus_assets import generate_assets

    generate_assets()


# HUD affinity gem sprites. The original four (fury/bastion/zephyr/holy) plus the
# discord/neutral faces shipped as hand-tuned 64x64 PNGs in the gui/sprites/hud
# atlas. The promoted four affinities (tide/forge/verdant/umbral) reuse the exact
# gem geometry by recolouring the neutral gem's luminance through a colour ramp
# built from each affinity's canonical ARGB, so the new gems match the family
# style and are tinted by the new palette. Deterministic: same input PNG and
# colour table always produce the same output bytes.
HUD_SPRITES = TEXTURES / "gui/sprites/hud"

# Canonical affinity colours, mirroring Affinity.argb() (RGB only).
NEW_AFFINITY_GEM_COLORS = {
    "tide": (0x2F, 0x7F, 0xD0),
    "forge": (0xC8, 0x5A, 0x2B),
    "verdant": (0x5F, 0xC2, 0x3E),
    "umbral": (0x7A, 0x4F, 0xB5),
}


def _gem_ramp(base):
    """A shadow -> mid -> highlight colour ramp anchored on the affinity colour."""
    shadow = tuple(round(c * 0.32) for c in base)
    highlight = tuple(round(c + (255 - c) * 0.78) for c in base)
    return shadow, base, highlight


def _ramp_sample(ramp, t):
    shadow, mid, high = ramp
    if t <= 0.5:
        f = t / 0.5
        a, b = shadow, mid
    else:
        f = (t - 0.5) / 0.5
        a, b = mid, high
    return tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3))


def hud_affinity_gems():
    template = Image.open(HUD_SPRITES / "affinity_neutral.png").convert("RGBA")
    width, height = template.size
    pixels = template.load()
    for name, color in NEW_AFFINITY_GEM_COLORS.items():
        ramp = _gem_ramp(color)
        out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        dst = out.load()
        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                if a == 0:
                    continue
                # Perceptual luminance of the neutral gem pixel, 0..1.
                lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                nr, ng, nb = _ramp_sample(ramp, lum)
                dst[x, y] = (nr, ng, nb, a)
        out.save(HUD_SPRITES / f"affinity_{name}.png")


if __name__ == "__main__":
    ensure_dirs()
    hud_affinity_gems()
    altar()
    reweaving_gui()
    focus_panel()
    satchel_gui()
    journal_gui()
    hud_backplate()
    journal()
    satchel_item()
    grand_satchel_item()
    generate_custom_focus_textures()
    generate_umbral_focus_textures()
