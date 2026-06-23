from __future__ import annotations

import json
import struct
import unittest
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate_modifier_focus_assets.py"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"

FOCUS_IDS = (
	"tidewarden_focus",
	"wellspring_focus",
	"current_runner_focus",
	"saltbrand_focus",
	"ebbstride_focus",
	"overgrowth_focus",
	"deeproot_focus",
	"briarcoat_focus",
	"fernstride_focus",
	"sapflow_focus",
	"cinderplate_focus",
	"bellowsfury_focus",
	"bloodrush_focus",
	"ravager_focus",
	"granitehide_focus",
	"hammerward_focus",
)

EXPECTED_MCMETA = {
	"animation": {
		"frametime": 2,
		"interpolate": True,
	}
}


def read_png(path: Path):
	data = path.read_bytes()
	if not data.startswith(b"\x89PNG\r\n\x1a\n"):
		raise AssertionError(f"not a PNG: {path}")
	offset = 8
	width = height = bit_depth = color_type = None
	idat = bytearray()
	while offset < len(data):
		length = struct.unpack(">I", data[offset:offset + 4])[0]
		chunk_type = data[offset + 4:offset + 8]
		chunk_data = data[offset + 8:offset + 8 + length]
		offset += 12 + length
		if chunk_type == b"IHDR":
			width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", chunk_data)
		elif chunk_type == b"IDAT":
			idat.extend(chunk_data)
		elif chunk_type == b"IEND":
			break
	if width is None or height is None or bit_depth is None or color_type is None:
		raise AssertionError(f"missing PNG IHDR: {path}")
	return width, height, bit_depth, color_type, zlib.decompress(bytes(idat))


def frame_rows(raw: bytes, *, width: int, frame: int, frame_height: int = 64) -> bytes:
	row_stride = 1 + width * 4
	start = frame * frame_height * row_stride
	end = start + frame_height * row_stride
	return raw[start:end]


def paeth_predictor(left: int, up: int, upper_left: int) -> int:
	prediction = left + up - upper_left
	left_distance = abs(prediction - left)
	up_distance = abs(prediction - up)
	upper_left_distance = abs(prediction - upper_left)
	if left_distance <= up_distance and left_distance <= upper_left_distance:
		return left
	if up_distance <= upper_left_distance:
		return up
	return upper_left


def unfilter_rgba_scanlines(raw: bytes, *, width: int, height: int) -> bytes:
	bytes_per_pixel = 4
	row_bytes = width * bytes_per_pixel
	output = bytearray(height * row_bytes)
	offset = 0
	for row in range(height):
		filter_type = raw[offset]
		offset += 1
		current = bytearray(raw[offset:offset + row_bytes])
		offset += row_bytes
		previous_start = (row - 1) * row_bytes
		current_start = row * row_bytes
		for index in range(row_bytes):
			left = current[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
			up = output[previous_start + index] if row > 0 else 0
			upper_left = output[previous_start + index - bytes_per_pixel] if row > 0 and index >= bytes_per_pixel else 0
			if filter_type == 0:
				value = current[index]
			elif filter_type == 1:
				value = current[index] + left
			elif filter_type == 2:
				value = current[index] + up
			elif filter_type == 3:
				value = current[index] + ((left + up) // 2)
			elif filter_type == 4:
				value = current[index] + paeth_predictor(left, up, upper_left)
			else:
				raise AssertionError(f"unsupported PNG filter {filter_type}")
			current[index] = value & 0xFF
			output[current_start + index] = current[index]
	return bytes(output)


def alpha_at(decoded_rgba: bytes, *, width: int, x: int, y: int) -> int:
	return decoded_rgba[(y * width + x) * 4 + 3]


class GenerateModifierFocusAssetsContractTest(unittest.TestCase):
	def test_generator_imports_private_source_sheet(self) -> None:
		source = GENERATOR.read_text(encoding="utf-8")

		self.assertIn("modifier-foci-source.png", source)
		self.assertIn(".attuned-art-sources", source)
		self.assertIn("asset-previews", source)
		self.assertIn("crop generated source grid cells", source)
		self.assertIn("remove flat magenta key background", source)
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
		):
			self.assertNotIn(forbidden, source)

	def test_generated_focus_assets_have_minecraft_sizes_and_animation(self) -> None:
		for focus_id in FOCUS_IDS:
			name = f"{focus_id}.png"
			path = TEXTURE_DIR / name
			width, height, bit_depth, color_type, raw = read_png(path)
			self.assertEqual((64, 512), (width, height), name)
			self.assertEqual(8, bit_depth, name)
			self.assertEqual(6, color_type, name)
			first = frame_rows(raw, width=width, frame=0)
			changed_frames = sum(
				frame_rows(raw, width=width, frame=frame) != first
				for frame in range(1, 8)
			)
			self.assertEqual(7, changed_frames, name)

			decoded = unfilter_rgba_scanlines(raw, width=width, height=height)
			for x, y in ((0, 0), (63, 0), (0, 63), (63, 63)):
				self.assertEqual(0, alpha_at(decoded, width=width, x=x, y=y), f"{name} corner {(x, y)}")

			mcmeta = json.loads(path.with_suffix(path.suffix + ".mcmeta").read_text(encoding="utf-8"))
			self.assertEqual(EXPECTED_MCMETA, mcmeta, name)


if __name__ == "__main__":
	unittest.main()
