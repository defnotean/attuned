import json
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

    # Pixel-cleaned from the image-generated leather/amethyst satchel concept,
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

    # Pixel-cleaned from the image-generated item source: chunky leather body,
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


def generate_custom_focus_textures():
    """Generates the deterministic default art + model/item JSON for the blank,
    resource-pack-skinnable Focus pool (attuned:custom_focus_1..N)."""
    import colorsys

    for n in range(1, CUSTOM_FOCUS_COUNT + 1):
        name = f"custom_focus_{n}"
        # Deterministic, byte-stable hue spread so all N differ and re-running is
        # reproducible. The pool ships static 16x16 art (no animation), unlike the
        # bespoke 64x512 shipped Foci.
        hue = ((n - 1) * 360 // CUSTOM_FOCUS_COUNT) / 360.0
        accent = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.62, 0.86)) + (255,)
        accent_light = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.34, 1.0)) + (255,)
        accent_dark = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.78, 0.48)) + (255,)
        rim = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.5, 0.96)) + (255,)

        _custom_focus_frame(accent, accent_light, accent_dark, rim).save(
            TEXTURES / "item" / f"{name}.png")

        model = {"parent": "minecraft:item/generated",
                 "textures": {"layer0": f"attuned:item/{name}"}}
        (ITEM_MODELS / f"{name}.json").write_text(
            json.dumps(model, indent=2) + "\n", encoding="utf-8")

        definition = {"model": {"type": "minecraft:model", "model": f"attuned:item/{name}"}}
        (ITEM_DEFINITIONS / f"{name}.json").write_text(
            json.dumps(definition, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    ensure_dirs()
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
