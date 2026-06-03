from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image


@dataclass(frozen=True)
class Cell:
    palette_index: int
    rgba: tuple[int, int, int, int]
    luma: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert a transparent item sprite into a Minecraft cuboid voxel model."
    )
    parser.add_argument("--sprite", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--palette", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--texture-id", required=True)
    parser.add_argument("--grid", default=32, type=int)
    parser.add_argument("--colors", default=28, type=int)
    parser.add_argument("--alpha", default=24, type=int)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    sprite = Image.open(args.sprite).convert("RGBA")
    grid = args.grid
    cells_rgba = sample_cells(sprite, grid, args.alpha)
    palette = build_palette(cells_rgba, args.colors)
    grid_cells = assign_cells(cells_rgba, palette)
    rectangles = greedy_rectangles(grid_cells)
    model = build_model(rectangles, palette, grid, args.texture_id)

    args.model.parent.mkdir(parents=True, exist_ok=True)
    args.palette.parent.mkdir(parents=True, exist_ok=True)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.model.write_text(json.dumps(model, indent="\t") + "\n", encoding="utf-8")
    write_palette(args.palette, palette)

    visible_cells = sum(1 for row in grid_cells for cell in row if cell is not None)
    report = {
        "source_sprite": str(args.sprite).replace("\\", "/"),
        "minecraft_model": str(args.model).replace("\\", "/"),
        "palette_texture": str(args.palette).replace("\\", "/"),
        "grid": grid,
        "requested_colors": args.colors,
        "palette_colors": len(palette),
        "visible_cells": visible_cells,
        "cuboids": len(rectangles),
        "compression_ratio": round(visible_cells / max(1, len(rectangles)), 2),
    }
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


def sample_cells(sprite: Image.Image, grid: int, alpha_threshold: int) -> list[list[tuple[int, int, int, int] | None]]:
    width, height = sprite.size
    pixels = sprite.load()
    sampled: list[list[tuple[int, int, int, int] | None]] = []
    for gy in range(grid):
        row: list[tuple[int, int, int, int] | None] = []
        y0 = int(gy * height / grid)
        y1 = max(y0 + 1, int((gy + 1) * height / grid))
        for gx in range(grid):
            x0 = int(gx * width / grid)
            x1 = max(x0 + 1, int((gx + 1) * width / grid))
            total_alpha = 0
            total_red = 0
            total_green = 0
            total_blue = 0
            samples = 0
            for y in range(y0, y1):
                for x in range(x0, x1):
                    red, green, blue, alpha = pixels[x, y]
                    if alpha <= alpha_threshold:
                        continue
                    total_alpha += alpha
                    total_red += red * alpha
                    total_green += green * alpha
                    total_blue += blue * alpha
                    samples += 1
            coverage = samples / ((x1 - x0) * (y1 - y0))
            if samples == 0 or coverage < 0.20:
                row.append(None)
                continue
            alpha = min(255, round(total_alpha / samples))
            row.append((
                round(total_red / total_alpha),
                round(total_green / total_alpha),
                round(total_blue / total_alpha),
                alpha,
            ))
        sampled.append(row)
    return sampled


def build_palette(cells: list[list[tuple[int, int, int, int] | None]], colors: int) -> list[tuple[int, int, int, int]]:
    swatch_source = [rgba for row in cells for rgba in row if rgba is not None]
    if not swatch_source:
        raise ValueError("No visible cells found in sprite.")

    strip = Image.new("RGB", (len(swatch_source), 1))
    for x, rgba in enumerate(swatch_source):
        strip.putpixel((x, 0), rgba[:3])
    quantized = strip.quantize(colors=min(colors, len(swatch_source)), method=Image.Quantize.MEDIANCUT)
    palette_rgb = quantized.getpalette() or []
    used = sorted(set(pixel_data(quantized)))
    palette: list[tuple[int, int, int, int]] = []
    for index in used:
        offset = index * 3
        palette.append((palette_rgb[offset], palette_rgb[offset + 1], palette_rgb[offset + 2], 255))

    if len(palette) > 256:
        raise ValueError("Minecraft palette texture supports at most 256 swatches.")
    return palette


def assign_cells(
    sampled: list[list[tuple[int, int, int, int] | None]],
    palette: list[tuple[int, int, int, int]],
) -> list[list[Cell | None]]:
    result: list[list[Cell | None]] = []
    for row in sampled:
        out_row: list[Cell | None] = []
        for rgba in row:
            if rgba is None:
                out_row.append(None)
                continue
            index = nearest_index(rgba, palette)
            red, green, blue, alpha = palette[index]
            luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            out_row.append(Cell(index, (red, green, blue, alpha), luma))
        result.append(out_row)
    return result


def nearest_index(color: tuple[int, int, int, int], palette: list[tuple[int, int, int, int]]) -> int:
    red, green, blue, _ = color
    best_index = 0
    best_distance = float("inf")
    for index, swatch in enumerate(palette):
        sr, sg, sb, _ = swatch
        distance = (red - sr) ** 2 + (green - sg) ** 2 + (blue - sb) ** 2
        if distance < best_distance:
            best_distance = distance
            best_index = index
    return best_index


def pixel_data(image: Image.Image) -> list[int]:
    if hasattr(image, "get_flattened_data"):
        return list(image.get_flattened_data())
    return list(image.getdata())


def greedy_rectangles(grid: list[list[Cell | None]]) -> list[tuple[int, int, int, int, Cell]]:
    height = len(grid)
    width = len(grid[0])
    seen = [[False] * width for _ in range(height)]
    rectangles: list[tuple[int, int, int, int, Cell]] = []
    for y in range(height):
        for x in range(width):
            cell = grid[y][x]
            if cell is None or seen[y][x]:
                continue

            rect_width = 1
            while x + rect_width < width:
                other = grid[y][x + rect_width]
                if other is None or seen[y][x + rect_width] or other.palette_index != cell.palette_index:
                    break
                rect_width += 1

            rect_height = 1
            while y + rect_height < height:
                for dx in range(rect_width):
                    other = grid[y + rect_height][x + dx]
                    if other is None or seen[y + rect_height][x + dx] or other.palette_index != cell.palette_index:
                        break
                else:
                    rect_height += 1
                    continue
                break

            for yy in range(y, y + rect_height):
                for xx in range(x, x + rect_width):
                    seen[yy][xx] = True
            rectangles.append((x, y, rect_width, rect_height, cell))
    return rectangles


def build_model(
    rectangles: list[tuple[int, int, int, int, Cell]],
    palette: list[tuple[int, int, int, int]],
    grid: int,
    texture_id: str,
) -> dict:
    unit = 16.0 / grid
    elements = []
    for x, y, width, height, cell in rectangles:
        px = cell.palette_index % 16
        py = cell.palette_index // 16
        uv = [px, py, px + 1, py + 1]
        luma = cell.luma / 255.0
        thickness = 0.95 + luma * 0.95
        z0 = round(8.0 - thickness / 2.0, 3)
        z1 = round(8.0 + thickness / 2.0, 3)
        x0 = round(x * unit, 3)
        x1 = round((x + width) * unit, 3)
        y0 = round(16.0 - (y + height) * unit, 3)
        y1 = round(16.0 - y * unit, 3)
        face = {"texture": "#palette", "uv": uv}
        elements.append({
            "from": [x0, y0, z0],
            "to": [x1, y1, z1],
            "shade": True,
            "faces": {
                "north": face,
                "south": face,
                "east": face,
                "west": face,
                "up": face,
                "down": face,
            },
        })

    return {
        "credit": "Generated from the Ocean Relic Trident sprite by tools/voxelize_item_sprite.py",
        "textures": {
            "palette": texture_id,
            "particle": texture_id,
        },
        "elements": elements,
        "display": {
            "gui": {
                "rotation": [0, 0, 0],
                "translation": [0, 0, 0],
                "scale": [1, 1, 1],
            },
            "firstperson_righthand": {
                "rotation": [12, -24, 0],
                "translation": [2.0, -1.0, 0],
                "scale": [1.0, 1.0, 1.0],
            },
            "thirdperson_righthand": {
                "rotation": [18, -32, 0],
                "translation": [0, 1.0, 0],
                "scale": [0.9, 0.9, 0.9],
            },
            "ground": {
                "rotation": [0, 0, 0],
                "translation": [0, 2, 0],
                "scale": [0.5, 0.5, 0.5],
            },
            "fixed": {
                "rotation": [15, -35, 0],
                "translation": [0, 0, 0],
                "scale": [0.9, 0.9, 0.9],
            },
        },
    }


def write_palette(path: Path, palette: list[tuple[int, int, int, int]]) -> None:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for index, rgba in enumerate(palette):
        image.putpixel((index % 16, index // 16), rgba)
    image.save(path)


if __name__ == "__main__":
    main()
