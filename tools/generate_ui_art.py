from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/attuned/textures"


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
    bevel(draw, 151, 31, 58, 24, (66, 59, 77, 255))
    rect(draw, (154, 34, 205, 51), (39, 36, 46, 255))
    draw.rectangle((155, 35, 204, 50), outline=(110, 91, 152, 255))

    # Inventory deck.
    rect(draw, (8, 94, 208, 95), STONE_DARK)
    rect(draw, (14, 101, 202, 178), (65, 60, 72, 255))
    draw.rectangle((14, 101, 202, 178), outline=(122, 114, 138, 255))
    for row in range(3):
        for col in range(9):
            slot_well(draw, 27 + col * 18, 108 + row * 18)
    for col in range(9):
        slot_well(draw, 27 + col * 18, 166)

    # Amethyst corner teeth.
    for x, y, sx, sy in ((3, 3, 1, 1), (212, 3, -1, 1), (3, 186, 1, -1), (212, 186, -1, -1)):
        draw.line((x, y, x + sx * 7, y), fill=AMETHYST_LIGHT)
        draw.line((x, y, x, y + sy * 7), fill=AMETHYST_LIGHT)
        draw.point((x + sx * 2, y + sy * 2), fill=AMETHYST)

    img.save(TEXTURES / "gui/altar.png")


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


if __name__ == "__main__":
    ensure_dirs()
    altar()
    focus_panel()
    hud_backplate()
    journal()
