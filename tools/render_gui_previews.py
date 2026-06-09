from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
CUSTOMIZER = ROOT / "tools/asset_customizer"
FIXTURES = CUSTOMIZER / "gui-fixtures.json"
OUTPUT = ROOT / "build/gui-previews"

BACKDROP = (8, 11, 16, 255)
PANEL = (15, 20, 28, 255)
SLOT = (50, 208, 212, 238)
REGION = (210, 134, 79, 244)
TEXT = (255, 228, 206, 238)


def main() -> None:
    parser = argparse.ArgumentParser(description="Render Attuned GUI previews without launching Minecraft.")
    parser.add_argument("--fixture", help="Only render the fixture with this id.")
    parser.add_argument("--scale", type=int, default=4, help="Nearest-neighbor scale for GUI textures.")
    args = parser.parse_args()

    OUTPUT.mkdir(parents=True, exist_ok=True)
    fixtures = load_fixtures()
    for fixture in fixtures:
        if args.fixture and fixture["id"] != args.fixture:
            continue
        output = render_fixture(fixture, args.scale)
        print(output.relative_to(ROOT).as_posix())


def load_fixtures() -> list[dict]:
    return json.loads(FIXTURES.read_text(encoding="utf-8"))


def render_fixture(fixture: dict, scale: int) -> Path:
    texture_path = (CUSTOMIZER / fixture["texture"]).resolve()
    texture = Image.open(texture_path).convert("RGBA")
    scaled = texture.resize((texture.width * scale, texture.height * scale), Image.Resampling.NEAREST)

    pad = 26
    title_h = 22
    canvas = Image.new("RGBA", (scaled.width + pad * 2, scaled.height + pad * 2 + title_h), BACKDROP)
    draw = ImageDraw.Draw(canvas)
    origin = (pad, pad + title_h)
    draw.rectangle(
        (origin[0] - 8, origin[1] - 8, origin[0] + scaled.width + 7, origin[1] + scaled.height + 7),
        fill=PANEL,
        outline=(52, 65, 84, 255),
    )
    canvas.alpha_composite(scaled, origin)
    draw.text((pad, 8), fixture["name"], fill=TEXT, font=ImageFont.load_default())

    slots = expanded_slots(fixture)
    draw_sample_items(draw, slots, origin, scale)
    draw_slot_overlay(draw, slots, origin, scale)
    draw_regions(draw, fixture.get("regions", []), origin, scale)

    output = OUTPUT / f"{fixture['id']}.png"
    canvas.save(output)
    return output


def expanded_slots(fixture: dict) -> list[dict]:
    slots: list[dict] = []
    for slot in fixture.get("slots", []):
        slots.append(
            {
                "x": slot["x"],
                "y": slot["y"],
                "w": slot.get("w", 16),
                "h": slot.get("h", 16),
                "sample": slot.get("sample", "inventory"),
                "index": len(slots),
            }
        )
    for group in fixture.get("slotGroups", []):
        for row in range(group["rows"]):
            for col in range(group["columns"]):
                slots.append(
                    {
                        "x": group["x"] + col * group["stepX"],
                        "y": group["y"] + row * group["stepY"],
                        "w": 16,
                        "h": 16,
                        "sample": group.get("sample", "inventory"),
                        "index": len(slots),
                    }
                )
    return slots


def draw_sample_items(draw: ImageDraw.ImageDraw, slots: list[dict], origin: tuple[int, int], scale: int) -> None:
    for slot in slots:
        x = origin[0] + (slot["x"] + 1) * scale
        y = origin[1] + (slot["y"] + 1) * scale
        size = max(4, min(slot.get("w", 16), slot.get("h", 16)) - 2) * scale
        dark, face, light = sample_palette(slot["sample"], slot["index"])
        draw.rectangle((x, y, x + size - 1, y + size - 1), fill=(6, 7, 12, 205))
        draw.rectangle((x + scale, y + scale, x + size - scale - 1, y + size - scale - 1), fill=dark)
        diamond = [
            (x + size // 2, y + scale * 2),
            (x + size - scale * 2, y + size // 2),
            (x + size // 2, y + size - scale * 2),
            (x + scale * 2, y + size // 2),
        ]
        draw.polygon(diamond, fill=face)
        draw.rectangle((x + size // 2 - scale, y + scale * 3, x + size // 2 + scale - 1, y + scale * 6 - 1), fill=light)


def draw_slot_overlay(draw: ImageDraw.ImageDraw, slots: list[dict], origin: tuple[int, int], scale: int) -> None:
    for slot in slots:
        x = origin[0] + slot["x"] * scale
        y = origin[1] + slot["y"] * scale
        draw.rectangle(
            (x, y, x + slot.get("w", 16) * scale - 1, y + slot.get("h", 16) * scale - 1),
            outline=SLOT,
            width=max(1, scale),
        )


def draw_regions(draw: ImageDraw.ImageDraw, regions: list[dict], origin: tuple[int, int], scale: int) -> None:
    font = ImageFont.load_default()
    for region in regions:
        x = origin[0] + region["x"] * scale
        y = origin[1] + region["y"] * scale
        w = region["w"] * scale
        h = region["h"] * scale
        draw.rectangle((x, y, x + w - 1, y + h - 1), outline=REGION, width=max(1, scale))
        label_y = max(0, y - 10)
        draw.text((x + 2, label_y), region["name"], fill=TEXT, font=font)


def sample_palette(kind: str, index: int) -> tuple[tuple[int, int, int, int], tuple[int, int, int, int], tuple[int, int, int, int]]:
    palettes = {
        "focus": ((13, 17, 24, 255), (121, 87, 214, 255), (213, 184, 255, 255)),
        "shard": ((16, 21, 29, 255), (79, 197, 230, 255), (212, 247, 255, 255)),
        "output": ((16, 19, 15, 255), (210, 134, 79, 255), (255, 228, 206, 255)),
        "hotbar": ((16, 21, 26, 255), (149, 212, 106, 255), (231, 255, 215, 255)),
        "inventory": ((16, 20, 28, 255), (74, 150, 216, 255), (212, 236, 255, 255)),
    }
    dark, face, light = palettes.get(kind, palettes["inventory"])
    multiplier = 1.0 - (index % 5) * 0.06
    shaded = tuple(max(0, min(255, round(channel * multiplier))) for channel in face[:3]) + (face[3],)
    return dark, shaded, light


if __name__ == "__main__":
    main()
