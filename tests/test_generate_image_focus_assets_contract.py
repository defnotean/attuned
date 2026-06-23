from __future__ import annotations

import json
import struct
import unittest
import zlib
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEM_TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"
UMBRAL_GENERATOR = ROOT / "tools" / "generate_umbral_eclipse_focus_assets.py"
CUSTOM_GENERATOR = ROOT / "tools" / "generate_custom_focus_assets.py"
ASPECT_REPLACEMENT_GENERATOR = ROOT / "tools" / "generate_aspect_counter_replacement_assets.py"
UPDraft_GENERATOR = ROOT / "tools" / "generate_updraft_focus_assets.py"
UI_GENERATOR = ROOT / "tools" / "generate_ui_art.py"

UMBRAL_FOCI = (
	"gloomstride_focus",
	"duskward_focus",
	"shadowmeld_focus",
	"dreadfang_focus",
	"eclipse_focus",
)

CUSTOM_FOCI = tuple(f"custom_focus_{index}" for index in range(1, 9))

ASPECT_REPLACEMENT_FOCI = (
	"bramblegate_focus",
	"seedcall_focus",
	"riptide_heart_focus",
	"pearlguard_focus",
	"slagbrand_focus",
)

EXPECTED_MCMETA = {
	"animation": {
		"frametime": 2,
		"interpolate": True,
	}
}


@dataclass(frozen=True)
class RgbaImage:
	width: int
	height: int
	pixels: bytes

	@property
	def mode(self) -> str:
		return "RGBA"

	@property
	def size(self) -> tuple[int, int]:
		return (self.width, self.height)

	def crop(self, left: int, top: int, right: int, bottom: int) -> "RgbaImage":
		width = right - left
		height = bottom - top
		rows = []
		for y in range(top, bottom):
			start = ((y * self.width) + left) * 4
			rows.append(self.pixels[start : start + width * 4])
		return RgbaImage(width, height, b"".join(rows))

	def getpixel(self, xy: tuple[int, int]) -> tuple[int, int, int, int]:
		x, y = xy
		offset = ((y * self.width) + x) * 4
		return tuple(self.pixels[offset : offset + 4])  # type: ignore[return-value]

	def tobytes(self) -> bytes:
		return self.pixels


def paeth_predictor(left: int, up: int, upper_left: int) -> int:
	p = left + up - upper_left
	pa = abs(p - left)
	pb = abs(p - up)
	pc = abs(p - upper_left)
	if pa <= pb and pa <= pc:
		return left
	if pb <= pc:
		return up
	return upper_left


def unfilter_scanline(filter_type: int, row: bytes, previous: bytes, bytes_per_pixel: int) -> bytes:
	out = bytearray(row)
	for index, value in enumerate(out):
		left = out[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
		up = previous[index] if previous else 0
		upper_left = previous[index - bytes_per_pixel] if previous and index >= bytes_per_pixel else 0
		if filter_type == 0:
			continue
		if filter_type == 1:
			out[index] = (value + left) & 0xFF
		elif filter_type == 2:
			out[index] = (value + up) & 0xFF
		elif filter_type == 3:
			out[index] = (value + ((left + up) // 2)) & 0xFF
		elif filter_type == 4:
			out[index] = (value + paeth_predictor(left, up, upper_left)) & 0xFF
		else:
			raise AssertionError(f"unsupported PNG filter type {filter_type}")
	return bytes(out)


def read_rgba_png(path: Path) -> RgbaImage:
	data = path.read_bytes()
	self_signature = b"\x89PNG\r\n\x1a\n"
	if not data.startswith(self_signature):
		raise AssertionError(f"{path.name} is not a PNG")

	offset = len(self_signature)
	width = 0
	height = 0
	idat = bytearray()
	while offset < len(data):
		length = struct.unpack(">I", data[offset : offset + 4])[0]
		chunk_type = data[offset + 4 : offset + 8]
		chunk_data = data[offset + 8 : offset + 8 + length]
		offset += 12 + length
		if chunk_type == b"IHDR":
			width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
				">IIBBBBB", chunk_data
			)
			if (bit_depth, color_type, compression, filter_method, interlace) != (8, 6, 0, 0, 0):
				raise AssertionError(f"{path.name} must be non-interlaced 8-bit RGBA PNG")
		elif chunk_type == b"IDAT":
			idat.extend(chunk_data)
		elif chunk_type == b"IEND":
			break
	if width <= 0 or height <= 0 or not idat:
		raise AssertionError(f"{path.name} is missing IHDR or IDAT data")

	raw = zlib.decompress(bytes(idat))
	stride = width * 4
	bytes_per_pixel = 4
	rows = []
	previous = b""
	cursor = 0
	for _row_index in range(height):
		filter_type = raw[cursor]
		cursor += 1
		filtered = raw[cursor : cursor + stride]
		cursor += stride
		row = unfilter_scanline(filter_type, filtered, previous, bytes_per_pixel)
		rows.append(row)
		previous = row
	return RgbaImage(width, height, b"".join(rows))


def alpha_bounds(image: RgbaImage) -> tuple[int, int, int, int]:
	min_x = image.width
	min_y = image.height
	max_x = 0
	max_y = 0
	for y in range(image.height):
		for x in range(image.width):
			if image.pixels[((y * image.width) + x) * 4 + 3] <= 8:
				continue
			min_x = min(min_x, x)
			min_y = min(min_y, y)
			max_x = max(max_x, x + 1)
			max_y = max(max_y, y + 1)
	if min_x == image.width:
		return (0, 0, 0, 0)
	return (min_x, min_y, max_x, max_y)


def frame(image: RgbaImage, index: int) -> RgbaImage:
	return image.crop(0, index * 64, 64, index * 64 + 64)


class GenerateImageFocusAssetsContractTest(unittest.TestCase):
	def test_generators_import_private_source_sheets(self) -> None:
		for generator, source_name in (
			(UMBRAL_GENERATOR, "umbral-eclipse-foci-source.png"),
			(CUSTOM_GENERATOR, "custom-foci-source.png"),
			(ASPECT_REPLACEMENT_GENERATOR, "aspect-counter-replacements-source.png"),
			(UPDraft_GENERATOR, "updraft-focus-source.png"),
		):
			source = generator.read_text(encoding="utf-8")
			self.assertIn(source_name, source)
			self.assertIn(".attuned-art-sources", source)
			self.assertIn("asset-previews", source)
			for forbidden in (
				"ImageDraw",
				"draw_",
				".line(",
				".rectangle(",
				".ellipse(",
				".polygon(",
				".arc(",
				"medallion(",
				"glyph",
				"pixel-clean",
				"cleaned deterministic",
			):
				self.assertNotIn(forbidden, source, generator.name)

		ui_source = UI_GENERATOR.read_text(encoding="utf-8")
		self.assertIn("from generate_umbral_eclipse_focus_assets import generate_assets", ui_source)
		self.assertIn("from generate_custom_focus_assets import generate_assets", ui_source)

	def assert_animated_focus_textures(self, item_ids: tuple[str, ...]) -> None:
		for item_id in item_ids:
			path = ITEM_TEXTURE_DIR / f"{item_id}.png"
			image = read_rgba_png(path)
			self.assertEqual("RGBA", image.mode, item_id)
			self.assertEqual((64, 512), image.size, item_id)
			first = frame(image, 0)
			changed = sum(frame(image, index).tobytes() != first.tobytes() for index in range(1, 8))
			self.assertEqual(7, changed, item_id)
			bounds = alpha_bounds(first)
			self.assertGreaterEqual(bounds[2] - bounds[0], 48, item_id)
			self.assertGreaterEqual(bounds[3] - bounds[1], 48, item_id)
			for xy in ((0, 0), (63, 0), (0, 63), (63, 63)):
				self.assertEqual(0, first.getpixel(xy)[3], f"{item_id} corner {xy}")

			mcmeta = json.loads(path.with_suffix(path.suffix + ".mcmeta").read_text(encoding="utf-8"))
			self.assertEqual(EXPECTED_MCMETA, mcmeta, item_id)

	def test_umbral_focus_replacements_are_animated_focus_textures(self) -> None:
		self.assert_animated_focus_textures(UMBRAL_FOCI)

	def test_aspect_counter_replacements_are_animated_focus_textures(self) -> None:
		self.assert_animated_focus_textures(ASPECT_REPLACEMENT_FOCI)

	def test_updraft_focus_is_animated_focus_texture(self) -> None:
		self.assert_animated_focus_textures(("updraft_focus",))

	def test_custom_focus_replacements_are_static_item_textures(self) -> None:
		for item_id in CUSTOM_FOCI:
			path = ITEM_TEXTURE_DIR / f"{item_id}.png"
			image = read_rgba_png(path)
			self.assertEqual("RGBA", image.mode, item_id)
			self.assertEqual((64, 64), image.size, item_id)
			bounds = alpha_bounds(image)
			self.assertGreaterEqual(bounds[2] - bounds[0], 48, item_id)
			self.assertGreaterEqual(bounds[3] - bounds[1], 48, item_id)
			for xy in ((0, 0), (63, 0), (0, 63), (63, 63)):
				self.assertEqual(0, image.getpixel(xy)[3], f"{item_id} corner {xy}")
			self.assertFalse(path.with_suffix(path.suffix + ".mcmeta").exists(), item_id)


if __name__ == "__main__":
	unittest.main()
