from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

SOURCE = Path(".attuned-art-sources/revenant-foci/revenant-foci-source.png")
TEXTURE_DIR = Path("src/main/resources/assets/attuned/textures/item")
REPORT = Path("build/asset-previews/revenant-foci/revenant-foci-report.json")

FOCI = [
	("epitaph_focus", (48, 160, 415, 615), (126, 250, 255)),
	("ashen_debt_focus", (426, 170, 790, 620), (224, 76, 64)),
	("hollowstep_focus", (820, 150, 1175, 630), (95, 242, 235)),
	("last_rites_focus", (1200, 160, 1548, 615), (255, 242, 178)),
	("bonechill_focus", (1585, 160, 1945, 630), (105, 215, 255)),
]


def main() -> None:
	source = Image.open(SOURCE).convert("RGBA")
	TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
	REPORT.parent.mkdir(parents=True, exist_ok=True)

	report = {
		"source": str(SOURCE).replace("\\", "/"),
		"strategy": "local_source_crops_with_pulsed_revenant_glow",
		"frames": 8,
		"textures": [],
	}
	for name, box, glow in FOCI:
		icon = cleaned_icon(source.crop(box))
		sheet = build_sheet(icon, glow)
		out = TEXTURE_DIR / f"{name}.png"
		sheet.save(out)
		(out.with_suffix(out.suffix + ".mcmeta")).write_text(json.dumps({
			"animation": {
				"frametime": 2,
				"interpolate": True,
			},
		}, indent=2) + "\n", encoding="utf-8")
		report["textures"].append({
			"name": name,
			"crop": list(box),
			"texture": str(out).replace("\\", "/"),
			"size": [sheet.width, sheet.height],
		})

	REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	print(json.dumps(report, indent=2))


def cleaned_icon(crop: Image.Image) -> Image.Image:
	pixels = crop.load()
	for y in range(crop.height):
		for x in range(crop.width):
			r, g, b, a = pixels[x, y]
			if is_chroma_green(r, g, b):
				pixels[x, y] = (r, g, b, 0)
			elif a > 0:
				pixels[x, y] = (r, g, b, 255)

	alpha = crop.getchannel("A")
	bbox = alpha.getbbox()
	if bbox is None:
		raise ValueError("generated crop is empty")
	crop = crop.crop(padded_box(bbox, crop.size, 12))
	return contain(crop, 64, 64, 4)


def build_sheet(icon: Image.Image, glow_color: tuple[int, int, int]) -> Image.Image:
	sheet = Image.new("RGBA", (64, 512), (0, 0, 0, 0))
	alpha = icon.getchannel("A")
	base_glow = Image.new("RGBA", icon.size, glow_color + (0,))
	base_glow.putalpha(alpha.filter(ImageFilter.GaussianBlur(3)))

	for frame in range(8):
		pulse = (math.sin((frame + 0.35) / 8.0 * math.tau) + 1.0) / 2.0
		glow_alpha = ImageEnhance.Brightness(base_glow.getchannel("A")).enhance(0.62 + pulse * 0.8)
		glow = Image.new("RGBA", icon.size, glow_color + (0,))
		glow.putalpha(glow_alpha)

		body = ImageEnhance.Brightness(icon).enhance(0.92 + pulse * 0.16)
		frame_image = Image.new("RGBA", icon.size, (0, 0, 0, 0))
		frame_image.alpha_composite(glow, (0, int(round((pulse - 0.5) * 2))))
		frame_image.alpha_composite(body)
		sheet.alpha_composite(frame_image, (0, frame * 64))
	return sheet


def contain(image: Image.Image, width: int, height: int, padding: int) -> Image.Image:
	image.thumbnail((width - padding * 2, height - padding * 2), Image.Resampling.LANCZOS)
	out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
	out.alpha_composite(image, ((width - image.width) // 2, (height - image.height) // 2))
	return out


def padded_box(box: tuple[int, int, int, int], size: tuple[int, int], padding: int) -> tuple[int, int, int, int]:
	left, top, right, bottom = box
	return (
		max(0, left - padding),
		max(0, top - padding),
		min(size[0], right + padding),
		min(size[1], bottom + padding),
	)


def is_chroma_green(red: int, green: int, blue: int) -> bool:
	return green > 155 and green > red * 1.7 and green > blue * 1.7 and red < 130 and blue < 130


if __name__ == "__main__":
	main()
