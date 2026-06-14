from __future__ import annotations

"""Generate simple, readable altar textures for Attuned.

These textures are intentionally low-frequency: dark stone carries the block
silhouette, while affinity color is reserved for crystals, runes, and thin
trim lines. The goal is a clean Minecraft block, not a noisy concept render.
"""

from math import sin, tau
from pathlib import Path
import random

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
TEXTURE_DIR = ROOT / "src/main/resources/assets/attuned/textures/block"
SIZE = 64
FRAMES = 8

AFFINITIES: dict[str, dict[str, tuple[int, int, int]]] = {
    "none": {"primary": (148, 88, 238), "secondary": (84, 204, 224), "spark": (224, 220, 255)},
    "fury": {"primary": (223, 72, 40), "secondary": (249, 139, 50), "spark": (255, 215, 120)},
    "bastion": {"primary": (75, 172, 96), "secondary": (189, 169, 88), "spark": (225, 245, 165)},
    "zephyr": {"primary": (58, 203, 224), "secondary": (96, 146, 235), "spark": (218, 252, 255)},
    "holy": {"primary": (218, 190, 91), "secondary": (238, 226, 154), "spark": (255, 250, 220)},
    "tide": {"primary": (28, 126, 154), "secondary": (72, 216, 226), "spark": (202, 255, 248)},
    "forge": {"primary": (204, 82, 31), "secondary": (232, 150, 50), "spark": (255, 226, 132)},
    "verdant": {"primary": (70, 132, 48), "secondary": (118, 206, 72), "spark": (220, 255, 166)},
    "umbral": {"primary": (94, 62, 158), "secondary": (154, 118, 232), "spark": (220, 194, 255)},
}

STATIC_OUTPUTS = (
    "attunement_altar_base.png",
    "altar_of_reweaving_base.png",
    "altar_of_reweaving_gem.png",
    "altar_of_reweaving_top.png",
)

ANIMATED_OUTPUTS = tuple(
    f"attunement_altar_{part}_{affinity}.png"
    for part in ("gem", "pillar", "top")
    for affinity in AFFINITIES
)


def clamp(value: int) -> int:
    return max(0, min(255, value))


def mix(a: tuple[int, int, int], b: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(clamp(round(a[i] * (1 - amount) + b[i] * amount)) for i in range(3))


def shade(color: tuple[int, int, int], amount: int) -> tuple[int, int, int]:
    return tuple(clamp(channel + amount) for channel in color)


def pulse(frame: int, phase: float = 0.0) -> float:
    return (sin((frame / FRAMES) * tau + phase) + 1) * 0.5


def rgba(color: tuple[int, int, int], alpha: int = 255) -> tuple[int, int, int, int]:
    return (*color, alpha)


def rect(draw: ImageDraw.ImageDraw, xy: tuple[int, int, int, int],
         color: tuple[int, int, int], alpha: int = 255) -> None:
    draw.rectangle(xy, fill=rgba(color, alpha))


def line(draw: ImageDraw.ImageDraw, points, color: tuple[int, int, int],
         alpha: int = 255, width: int = 1) -> None:
    draw.line(points, fill=rgba(color, alpha), width=width)


def diamond(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int,
            color: tuple[int, int, int], alpha: int = 255) -> None:
    draw.polygon(
        [(cx, cy - radius), (cx + radius, cy), (cx, cy + radius), (cx - radius, cy)],
        fill=rgba(color, alpha),
    )


def outline_rect(draw: ImageDraw.ImageDraw, xy: tuple[int, int, int, int],
                 light: tuple[int, int, int], dark: tuple[int, int, int],
                 alpha: int = 180) -> None:
    x0, y0, x1, y1 = xy
    line(draw, [(x0, y1), (x0, y0), (x1, y0)], light, alpha)
    line(draw, [(x1, y0), (x1, y1), (x0, y1)], dark, alpha)


def base(color: tuple[int, int, int]) -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), rgba(color))


def add_stone_noise(image: Image.Image, seed: str, strength: int = 5, density: float = 0.12) -> None:
    rng = random.Random(seed)
    pixels = image.load()
    for _ in range(round(SIZE * SIZE * density)):
        x = rng.randrange(SIZE)
        y = rng.randrange(SIZE)
        r, g, b, a = pixels[x, y]
        delta = rng.randint(-strength, strength)
        pixels[x, y] = (clamp(r + delta), clamp(g + delta), clamp(b + delta), a)


def stone_tiles(draw: ImageDraw.ImageDraw, light=(48, 53, 68), dark=(9, 11, 16)) -> None:
    for xy in ((0, 0, 63, 63), (4, 4, 59, 59), (11, 11, 52, 52)):
        outline_rect(draw, xy, light, dark, 150)
    for x in (17, 33, 49):
        line(draw, [(x, 5), (x, 13)], light, 80)
        line(draw, [(x, 50), (x, 58)], dark, 90)
    for y in (17, 33, 49):
        line(draw, [(5, y), (13, y)], light, 80)
        line(draw, [(50, y), (58, y)], dark, 90)


def corner_caps(draw: ImageDraw.ImageDraw, color=(194, 177, 139), alpha: int = 170) -> None:
    for x, y in ((4, 4), (53, 4), (4, 53), (53, 53)):
        rect(draw, (x, y, x + 6, y + 2), color, alpha)
        rect(draw, (x, y, x + 2, y + 6), color, alpha)


def make_attunement_base() -> Image.Image:
    image = base((20, 23, 31))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, "simple-attunement-base", 4, 0.1)
    stone_tiles(draw)
    outline_rect(draw, (1, 1, 62, 62), (66, 70, 84), (5, 7, 11), 190)
    outline_rect(draw, (8, 8, 55, 55), (42, 48, 62), (8, 9, 14), 150)
    outline_rect(draw, (20, 20, 44, 44), (44, 51, 66), (7, 9, 14), 135)
    for offset in (15, 24, 40, 49):
        line(draw, [(offset, 6), (offset, 10)], (58, 190, 205), 80)
        line(draw, [(6, offset), (10, offset)], (58, 190, 205), 80)
        line(draw, [(offset, 54), (offset, 58)], (112, 86, 190), 55)
        line(draw, [(54, offset), (58, offset)], (112, 86, 190), 55)
    corner_caps(draw, alpha=150)
    return image


def make_reweaving_base() -> Image.Image:
    image = base((18, 21, 28))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, "simple-reweaving-base", 4, 0.09)
    stone_tiles(draw, (45, 51, 65), (6, 8, 12))
    outline_rect(draw, (2, 2, 61, 61), (60, 66, 82), (5, 7, 11), 180)
    outline_rect(draw, (10, 13, 53, 50), (41, 48, 62), (7, 9, 14), 145)
    for y in (18, 45):
        line(draw, [(14, y), (50, y)], (55, 194, 205), 95)
    for x in (18, 45):
        line(draw, [(x, 18), (x, 45)], (120, 82, 190), 60)
    for x, y in ((14, 14), (45, 14), (14, 45), (45, 45)):
        rect(draw, (x, y, x + 4, y + 4), (46, 56, 70), 150)
    return image


def make_reweaving_gem() -> Image.Image:
    image = base((13, 17, 24))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, "simple-reweaving-gem", 3, 0.06)
    outline_rect(draw, (4, 4, 59, 59), (57, 65, 84), (6, 8, 13), 180)
    outline_rect(draw, (11, 10, 52, 54), (43, 50, 64), (7, 9, 14), 150)
    diamond(draw, 32, 31, 19, (37, 172, 194), 210)
    diamond(draw, 32, 31, 12, (75, 213, 225), 220)
    diamond(draw, 32, 31, 5, (224, 253, 255), 235)
    line(draw, [(32, 9), (32, 53)], (116, 82, 196), 105, 2)
    line(draw, [(14, 31), (50, 31)], (55, 194, 205), 95)
    corner_caps(draw, (170, 154, 122), 95)
    return image


def make_reweaving_top() -> Image.Image:
    image = base((15, 18, 25))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, "simple-reweaving-top", 3, 0.07)
    outline_rect(draw, (3, 3, 60, 60), (62, 69, 86), (5, 7, 11), 185)
    outline_rect(draw, (10, 10, 53, 53), (42, 48, 62), (6, 8, 13), 145)
    rect(draw, (15, 15, 49, 49), (21, 25, 34), 255)
    for y, color, alpha in (
        (22, (54, 197, 211), 150),
        (27, (118, 82, 194), 140),
        (32, (54, 197, 211), 165),
        (37, (118, 82, 194), 140),
        (42, (54, 197, 211), 145),
    ):
        line(draw, [(17, y), (47, y)], color, alpha, 2)
    diamond(draw, 32, 32, 8, (64, 207, 219), 150)
    diamond(draw, 32, 32, 4, (210, 247, 252), 190)
    corner_caps(draw, (170, 154, 122), 120)
    return image


def affinity_color(affinity: str, key: str, frame: int, amount: float = 0.2) -> tuple[int, int, int]:
    palette = AFFINITIES[affinity]
    return mix(palette[key], palette["spark"], amount * pulse(frame))


def make_attunement_pillar(affinity: str, frame: int) -> Image.Image:
    palette = AFFINITIES[affinity]
    primary = affinity_color(affinity, "primary", frame, 0.24)
    secondary = affinity_color(affinity, "secondary", frame, 0.18)
    image = base((17, 20, 28))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, f"simple-pillar-{affinity}-{frame}", 4, 0.08)
    rect(draw, (0, 0, 8, 63), (9, 11, 17), 255)
    rect(draw, (56, 0, 63, 63), (8, 10, 15), 255)
    rect(draw, (9, 0, 55, 63), (24, 28, 38), 255)
    outline_rect(draw, (9, 0, 55, 63), (50, 56, 70), (5, 7, 11), 135)
    outline_rect(draw, (15, 8, 49, 56), (36, 43, 56), (8, 10, 16), 100)
    for x in (21, 42):
        line(draw, [(x, 10), (x, 54)], primary, 95 + round(45 * pulse(frame)), 2)
    for y in (14, 25, 39, 50):
        line(draw, [(18, y), (46, y)], (8, 10, 15), 155)
        diamond(draw, 32, y + 2, 3, secondary, 105 + round(45 * pulse(frame, y / 8)))
    line(draw, [(31, 15), (31, 49)], palette["spark"], 35 + round(25 * pulse(frame)), 1)
    return image


def make_attunement_gem(affinity: str, frame: int) -> Image.Image:
    palette = AFFINITIES[affinity]
    primary = affinity_color(affinity, "primary", frame, 0.3)
    secondary = affinity_color(affinity, "secondary", frame, 0.22)
    spark = palette["spark"]
    image = base((12, 15, 23))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, f"simple-gem-{affinity}-{frame}", 3, 0.05)
    outline_rect(draw, (3, 3, 60, 60), (60, 66, 82), (5, 7, 11), 180)
    outline_rect(draw, (10, 8, 53, 56), (45, 51, 65), (8, 10, 15), 145)
    diamond(draw, 32, 31, 22, primary, 205)
    diamond(draw, 32, 31, 14, secondary, 215)
    diamond(draw, 32, 31, 6, spark, 225)
    line(draw, [(32, 10), (32, 52)], spark, 95 + round(45 * pulse(frame)), 1)
    line(draw, [(16, 31), (48, 31)], secondary, 90, 1)
    corner_caps(draw, (185, 168, 132), 110)
    return image


def make_attunement_top(affinity: str, frame: int) -> Image.Image:
    palette = AFFINITIES[affinity]
    primary = affinity_color(affinity, "primary", frame, 0.22)
    secondary = affinity_color(affinity, "secondary", frame, 0.16)
    image = base((16, 19, 27))
    draw = ImageDraw.Draw(image, "RGBA")
    add_stone_noise(image, f"simple-top-{affinity}-{frame}", 3, 0.07)
    outline_rect(draw, (3, 3, 60, 60), (64, 70, 86), (5, 7, 11), 185)
    outline_rect(draw, (10, 10, 53, 53), (40, 47, 61), (7, 9, 14), 145)
    outline_rect(draw, (17, 17, 46, 46), (35, 42, 56), (8, 10, 16), 110)
    line(draw, [(16, 32), (25, 32), (29, 28)], primary, 150, 2)
    line(draw, [(48, 32), (39, 32), (35, 36)], primary, 150, 2)
    line(draw, [(32, 16), (32, 25), (28, 29)], secondary, 145, 2)
    line(draw, [(32, 48), (32, 39), (36, 35)], secondary, 145, 2)
    diamond(draw, 32, 32, 10, primary, 130)
    diamond(draw, 32, 32, 5, palette["spark"], 205)
    for offset in (13, 50):
        line(draw, [(offset, 8), (offset, 14)], primary, 100)
        line(draw, [(8, offset), (14, offset)], primary, 100)
        line(draw, [(offset, 50), (offset, 56)], secondary, 85)
        line(draw, [(50, offset), (56, offset)], secondary, 85)
    corner_caps(draw, (185, 168, 132), 100)
    return image


def animated_strip(factory) -> Image.Image:
    strip = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    for frame in range(FRAMES):
        strip.alpha_composite(factory(frame), (0, frame * SIZE))
    return strip


def write_png(name: str, image: Image.Image) -> None:
    out = TEXTURE_DIR / name
    image.save(out)
    print(f"wrote {out.relative_to(ROOT).as_posix()}")


def generate_all() -> None:
    write_png("attunement_altar_base.png", make_attunement_base())
    write_png("altar_of_reweaving_base.png", make_reweaving_base())
    write_png("altar_of_reweaving_gem.png", make_reweaving_gem())
    write_png("altar_of_reweaving_top.png", make_reweaving_top())
    for affinity in AFFINITIES:
        write_png(
            f"attunement_altar_gem_{affinity}.png",
            animated_strip(lambda frame, affinity=affinity: make_attunement_gem(affinity, frame)),
        )
        write_png(
            f"attunement_altar_pillar_{affinity}.png",
            animated_strip(lambda frame, affinity=affinity: make_attunement_pillar(affinity, frame)),
        )
        write_png(
            f"attunement_altar_top_{affinity}.png",
            animated_strip(lambda frame, affinity=affinity: make_attunement_top(affinity, frame)),
        )


if __name__ == "__main__":
    generate_all()
