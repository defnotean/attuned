from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from math import sqrt
from pathlib import Path
import shutil

from PIL import Image, ImageChops, ImageStat


ROOT = Path(__file__).resolve().parents[1]
TEXTURE_ROOT = ROOT / "src/main/resources/assets/attuned/textures"
SOURCE_MESH_TEXTURE = TEXTURE_ROOT / "item/ocean_relic_trident_blockbench.png"
SOURCE_MESH_TEXTURE_META = TEXTURE_ROOT / "item/ocean_relic_trident_blockbench.png.mcmeta"
RUNTIME_MESH_TEXTURE = TEXTURE_ROOT / "item/ocean_relic_trident_mesh.png"
RUNTIME_MESH_TEXTURE_META = TEXTURE_ROOT / "item/ocean_relic_trident_mesh.png.mcmeta"
RUNTIME_MESH_SIZE = (512, 512)

COLOR_REDUCTION_RMS_LIMIT = 6.0
COLOR_REDUCTION_MAX_DELTA_LIMIT = 64
COLOR_REDUCTION_MIN_SIZE_RATIO = 0.98


@dataclass(frozen=True)
class OptimizationResult:
    path: Path
    old_size: int
    new_size: int
    mode: str


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=True, compress_level=9)
    return output.getvalue()


def difference_stats(original: Image.Image, candidate: Image.Image) -> tuple[float, int]:
    diff = ImageChops.difference(original.convert("RGBA"), candidate.convert("RGBA"))
    stat = ImageStat.Stat(diff)
    rms = sqrt(sum(channel * channel for channel in stat.rms) / len(stat.rms))
    max_delta = max(maximum for _minimum, maximum in diff.getextrema())
    return rms, max_delta


def color_reduced_rgba_png_bytes(original: Image.Image, original_size: int) -> bytes | None:
    rgba = original.convert("RGBA")
    alpha = rgba.getchannel("A")
    palette = rgba.convert("RGB").quantize(
        colors=256,
        method=Image.Quantize.FASTOCTREE,
        dither=Image.Dither.NONE,
    )
    candidate = palette.convert("RGBA")
    candidate.putalpha(alpha)
    candidate_bytes = png_bytes(candidate)
    if len(candidate_bytes) >= int(original_size * COLOR_REDUCTION_MIN_SIZE_RATIO):
        return None

    with Image.open(BytesIO(candidate_bytes)) as candidate:
        candidate.load()
        rms, max_delta = difference_stats(rgba, candidate)
    if rms > COLOR_REDUCTION_RMS_LIMIT or max_delta > COLOR_REDUCTION_MAX_DELTA_LIMIT:
        return None
    return candidate_bytes


def optimize_png(path: Path) -> OptimizationResult:
    old_bytes = path.read_bytes()
    old_size = len(old_bytes)
    with Image.open(path) as image:
        image.load()
        original = image.copy()

    best_bytes = old_bytes
    mode = "unchanged"

    lossless_bytes = png_bytes(original)
    if len(lossless_bytes) < len(best_bytes):
        best_bytes = lossless_bytes
        mode = "lossless"

    if can_color_reduce(path):
        color_reduced_bytes = color_reduced_rgba_png_bytes(original, old_size)
        if color_reduced_bytes is not None and len(color_reduced_bytes) < len(best_bytes):
            best_bytes = color_reduced_bytes
            mode = "reduced-rgba"

    if best_bytes != old_bytes:
        path.write_bytes(best_bytes)

    return OptimizationResult(path=path, old_size=old_size, new_size=len(best_bytes), mode=mode)


def can_color_reduce(path: Path) -> bool:
    rel = path.relative_to(TEXTURE_ROOT).as_posix()
    return not rel.startswith("block/attunement_altar_")


def write_runtime_mesh_texture() -> None:
    with Image.open(SOURCE_MESH_TEXTURE) as source:
        source.load()
        runtime = source.convert("RGBA").resize(RUNTIME_MESH_SIZE, Image.Resampling.LANCZOS)
    RUNTIME_MESH_TEXTURE.write_bytes(png_bytes(runtime))
    shutil.copyfile(SOURCE_MESH_TEXTURE_META, RUNTIME_MESH_TEXTURE_META)


def texture_paths() -> list[Path]:
    excluded = {SOURCE_MESH_TEXTURE.resolve()}
    return [
        path
        for path in sorted(TEXTURE_ROOT.rglob("*.png"))
        if path.resolve() not in excluded
    ]


def main() -> int:
    write_runtime_mesh_texture()
    results = [optimize_png(path) for path in texture_paths()]
    changed = [result for result in results if result.old_size != result.new_size]
    reduced = [result for result in changed if result.mode == "reduced-rgba"]
    old_total = sum(result.old_size for result in results)
    new_total = sum(result.new_size for result in results)

    print(f"Optimized {len(changed)} of {len(results)} textures.")
    print(f"Color-reduced {len(reduced)} RGBA textures.")
    print(f"Texture bytes: {old_total:,} -> {new_total:,} ({old_total - new_total:,} saved).")
    for result in sorted(changed, key=lambda item: item.old_size - item.new_size, reverse=True)[:20]:
        rel = result.path.relative_to(ROOT)
        saved = result.old_size - result.new_size
        print(f"{rel}: {result.old_size:,} -> {result.new_size:,} ({saved:,} saved, {result.mode})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
