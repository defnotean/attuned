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
    img = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(img)
    rect(draw, (2, 1, 12, 14), (45, 31, 58, 255))
    draw.line((3, 1, 12, 1), fill=(95, 71, 122, 255))
    draw.line((2, 2, 2, 13), fill=(19, 14, 25, 255))
    draw.line((12, 2, 12, 14), fill=(18, 13, 24, 255))
    rect(draw, (4, 3, 10, 12), (72, 48, 92, 255))
    draw.line((5, 4, 9, 4), fill=AMETHYST_LIGHT)
    draw.line((5, 6, 9, 6), fill=AMETHYST)
    draw.line((5, 8, 8, 8), fill=(176, 148, 214, 255))
    draw.rectangle((10, 6, 14, 9), fill=(190, 151, 73, 255), outline=(82, 55, 20, 255))
    draw.point((12, 7), fill=AMETHYST_LIGHT)
    draw.line((4, 13, 12, 13), fill=(18, 13, 24, 255))
    img.save(TEXTURES / "item/attunement_journal.png")


if __name__ == "__main__":
    ensure_dirs()
    altar()
    focus_panel()
    hud_backplate()
    journal()
