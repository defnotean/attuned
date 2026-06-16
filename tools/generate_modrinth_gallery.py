from __future__ import annotations

import json
import math
import textwrap
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
FOCUS_DATA_DIR = ROOT / "src/main/resources/data/attuned/attuned/focus"
ITEM_TEXTURE_DIR = ROOT / "src/main/resources/assets/attuned/textures/item"
HUD_TEXTURE_DIR = ROOT / "src/main/resources/assets/attuned/textures/gui/sprites/hud"
LANG_FILE = ROOT / "src/main/resources/assets/attuned/lang/en_us.json"
OUT_DIR = ROOT / "docs/modrinth-gallery"

WIDTH = 1920
HEIGHT = 1080


AFFINITY_LABELS = {
	"fury": "Fury",
	"bastion": "Bastion",
	"zephyr": "Zephyr",
	"holy": "Holy",
	"tide": "Tide",
	"forge": "Forge",
	"verdant": "Verdant",
	"umbral": "Umbral",
	"neutral": "Neutral",
}

AFFINITY_COLORS = {
	"fury": (230, 83, 46),
	"bastion": (84, 180, 116),
	"zephyr": (77, 178, 235),
	"holy": (255, 218, 105),
	"tide": (64, 177, 222),
	"forge": (230, 124, 60),
	"verdant": (112, 196, 94),
	"umbral": (168, 88, 220),
	"neutral": (176, 170, 190),
}

PANEL_ORDER = [
	("fury", "Fury Foci", "Fast, violent relics for pressure, burns, lifesteal, and decisive openings."),
	("bastion", "Bastion Foci", "Defensive relics for armor, resistance, survival, and held ground."),
	("zephyr", "Zephyr Foci", "Mobility relics for speed, air, weather, travel, and quiet movement."),
	("holy", "Holy Foci", "Radiant and reliquary relics built around light, vows, witness, and protection."),
	("tide", "Tide Foci", "Water, current, pearl, and seafaring relics for swimming, control, and resilient pressure."),
	("forge", "Forge Foci", "Cinder, anvil, kiln, and spark relics for heat, armor, toughness, and tempo."),
	("verdant", "Verdant Foci", "Root, fern, sap, thorn, and seed relics for growth, vitality, and mobile defense."),
]

ITEM_PANEL_ASSETS = (
	("item", "attunement_shard"),
	("item", "attunement_shard_fragment"),
	("item", "attunement_journal"),
	("item", "satchel_of_foci"),
	("item", "grand_satchel_of_foci"),
	("item", "ocean_relic_trident"),
	("item", "frostbound_trident"),
	("item", "offshore_harpoon"),
	("item", "custom_focus_1"),
	("item", "custom_focus_2"),
	("item", "custom_focus_3"),
	("item", "custom_focus_4"),
	("item", "custom_focus_5"),
	("item", "custom_focus_6"),
	("item", "custom_focus_7"),
	("item", "custom_focus_8"),
	("block", "attunement_altar_top_fury"),
	("block", "attunement_altar_top_bastion"),
	("block", "attunement_altar_top_zephyr"),
	("block", "attunement_altar_top_holy"),
	("block", "attunement_altar_top_tide"),
	("block", "attunement_altar_top_forge"),
	("block", "attunement_altar_top_verdant"),
	("block", "attunement_altar_top_umbral"),
	("block", "altar_of_reweaving_top"),
)


@dataclass(frozen=True)
class Focus:
	id: str
	name: str
	cost: int
	affinity: str
	faction: str
	lore: tuple[str, str]
	effect: str
	texture: Path


@dataclass(frozen=True)
class GalleryAsset:
	id: str
	name: str
	kind: str
	texture: Path
	description: str


def load_font(name: str, size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
	candidates = [
		Path("C:/Windows/Fonts") / name,
		Path("C:/Windows/Fonts/segoeui.ttf"),
		Path("C:/Windows/Fonts/arial.ttf"),
		# Linux/macOS fallbacks so the generator stays usable off-Windows.
		Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if "b" in name else
			"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
		Path("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
		Path("/System/Library/Fonts/Helvetica.ttc"),
	]
	for candidate in candidates:
		if candidate.exists():
			return ImageFont.truetype(str(candidate), size)
	try:
		# Pillow >= 10.1 supports sized default fonts; without a size the
		# bitmap default lacks .size and breaks draw_text's line metrics.
		return ImageFont.load_default(size=size)
	except TypeError:
		return ImageFont.load_default()


FONT_TITLE = load_font("segoeuib.ttf", 52)
FONT_SUBTITLE = load_font("segoeui.ttf", 25)
FONT_CARD_TITLE = load_font("segoeuib.ttf", 24)
FONT_LABEL = load_font("segoeuib.ttf", 16)
FONT_BODY = load_font("segoeui.ttf", 17)
FONT_BODY_BOLD = load_font("segoeuib.ttf", 17)
FONT_SMALL = load_font("segoeui.ttf", 15)
FONT_APEX_TITLE = load_font("segoeuib.ttf", 29)
FONT_APEX_BODY = load_font("segoeui.ttf", 19)
FONT_APEX_SMALL = load_font("segoeui.ttf", 16)


def clean_faction(faction: str) -> str:
	if not faction:
		return "None"
	raw = faction.split(":", 1)[-1]
	return raw.replace("_", " ").title()


def title_from_id(item_id: str) -> str:
	return item_id.replace("_", " ").title()


def load_foci() -> list[Focus]:
	lang = json.loads(LANG_FILE.read_text(encoding="utf-8"))
	foci: list[Focus] = []
	for path in sorted(FOCUS_DATA_DIR.glob("*_focus.json")):
		data = json.loads(path.read_text(encoding="utf-8"))
		item_id = data["item"].split(":", 1)[1]
		key = f"item.attuned.{item_id}"
		if key not in lang:
			raise KeyError(f"Missing language entry '{key}' for {path.name} in {LANG_FILE}")
		texture = ITEM_TEXTURE_DIR / f"{item_id}.png"
		if not texture.exists():
			raise FileNotFoundError(f"Missing texture for {item_id}: {texture}")
		foci.append(
			Focus(
				id=item_id,
				name=lang[key],
				cost=int(data.get("cost", 1)),
				affinity=data.get("affinity", "neutral"),
				faction=clean_faction(data.get("faction", "")),
				lore=(
					lang.get(f"{key}.lore", ""),
					lang.get(f"{key}.lore2", ""),
				),
				effect=lang.get(f"{key}.effect", ""),
				texture=texture,
			)
		)
	return foci


def load_gallery_assets() -> list[GalleryAsset]:
	lang = json.loads(LANG_FILE.read_text(encoding="utf-8"))
	descriptions = {
		"attunement_shard": "Capacity progression item used at the Attunement Altar.",
		"attunement_shard_fragment": "Smaller loot reward; four fragments craft into one shard.",
		"attunement_journal": "The in-game guide for Foci, pacts, affinities, and matchup reference.",
		"satchel_of_foci": "Focus Reliquary storage with equipped-slot management and saved builds.",
		"grand_satchel_of_foci": "Expanded 54-slot reliquary for larger Focus collections.",
		"ocean_relic_trident": "Seafaring trident model with custom inventory and held art.",
		"frostbound_trident": "Icy trident model with its own voxel-style item art.",
		"offshore_harpoon": "Offshore utility weapon art alongside the seafaring relic set.",
		"custom_focus_1": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_2": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_3": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_4": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_5": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_6": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_7": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"custom_focus_8": "Resource-pack-skinnable blank Focus shell for datapack authors.",
		"attunement_altar_top_fury": "Fury altar variant texture.",
		"attunement_altar_top_bastion": "Bastion altar variant texture.",
		"attunement_altar_top_zephyr": "Zephyr altar variant texture.",
		"attunement_altar_top_holy": "Holy altar variant texture.",
		"attunement_altar_top_tide": "Tide altar variant texture.",
		"attunement_altar_top_forge": "Forge altar variant texture.",
		"attunement_altar_top_verdant": "Verdant altar variant texture.",
		"attunement_altar_top_umbral": "Umbral altar variant texture.",
		"altar_of_reweaving_top": "Altar of Reweaving top texture.",
	}
	assets: list[GalleryAsset] = []
	for kind, asset_id in ITEM_PANEL_ASSETS:
		texture_dir = ITEM_TEXTURE_DIR if kind == "item" else ROOT / "src/main/resources/assets/attuned/textures/block"
		texture = texture_dir / f"{asset_id}.png"
		if not texture.exists():
			raise FileNotFoundError(f"Missing gallery asset texture for {asset_id}: {texture}")
		lang_key = f"item.attuned.{asset_id}" if kind == "item" else f"block.attuned.{asset_id}"
		name = lang.get(lang_key, title_from_id(asset_id))
		assets.append(GalleryAsset(asset_id, name, kind.title(), texture, descriptions[asset_id]))
	return assets


def first_frame(path: Path) -> Image.Image:
	img = Image.open(path).convert("RGBA")
	frame_size = img.width
	return img.crop((0, 0, frame_size, min(frame_size, img.height)))


def rounded_rectangle(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], radius: int, fill, outline=None, width=1):
	draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, font, fill, max_width: int, line_spacing: int = 4, max_lines: int | None = None) -> int:
	if not text:
		return xy[1]
	lines: list[str] = []
	for paragraph in text.splitlines():
		words = paragraph.split()
		current = ""
		for word in words:
			candidate = word if not current else f"{current} {word}"
			if draw.textlength(candidate, font=font) <= max_width:
				current = candidate
			else:
				if current:
					lines.append(current)
				current = word
		if current:
			lines.append(current)
	if max_lines is not None and len(lines) > max_lines:
		lines = lines[:max_lines]
		while lines[-1] and draw.textlength(lines[-1] + "...", font=font) > max_width:
			lines[-1] = lines[-1][:-1]
		lines[-1] += "..."
	y = xy[1]
	for line in lines:
		draw.text((xy[0], y), line, font=font, fill=fill)
		y += font.size + line_spacing
	return y


def make_background(accent: tuple[int, int, int]) -> Image.Image:
	bg = Image.new("RGB", (WIDTH, HEIGHT), (13, 15, 21))
	draw = ImageDraw.Draw(bg)

	for y in range(0, HEIGHT, 48):
		for x in range(0, WIDTH, 48):
			tone = 18 + ((x // 48 + y // 48) % 2) * 4
			draw.rectangle((x, y, x + 47, y + 47), fill=(tone, tone + 2, tone + 8))
			draw.rectangle((x, y, x + 47, y + 47), outline=(31, 33, 43))

	overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
	od = ImageDraw.Draw(overlay)
	for radius, alpha in [(760, 46), (520, 40), (300, 34)]:
		box = (WIDTH // 2 - radius, -radius // 2, WIDTH // 2 + radius, radius + 250)
		od.ellipse(box, fill=(*accent, alpha))
	overlay = overlay.filter(ImageFilter.GaussianBlur(80))
	return Image.alpha_composite(bg.convert("RGBA"), overlay)


def chip(draw: ImageDraw.ImageDraw, x: int, y: int, label: str, fill: tuple[int, int, int], text_fill=(248, 248, 250)) -> int:
	padding_x = 12
	w = int(draw.textlength(label, font=FONT_LABEL)) + padding_x * 2
	h = 28
	rounded_rectangle(draw, (x, y, x + w, y + h), 7, (*fill, 220), outline=(*fill, 255), width=1)
	draw.text((x + padding_x, y + 5), label, font=FONT_LABEL, fill=text_fill)
	return x + w + 8


def draw_focus_card(canvas: Image.Image, focus: Focus, box: tuple[int, int, int, int]) -> None:
	draw = ImageDraw.Draw(canvas)
	x0, y0, x1, y1 = box
	accent = AFFINITY_COLORS[focus.affinity]
	rounded_rectangle(draw, box, 12, (25, 27, 36, 232), outline=(*accent, 230), width=2)
	draw.rectangle((x0 + 2, y0 + 2, x1 - 2, y0 + 48), fill=(*accent, 34))

	icon_back = (x0 + 18, y0 + 62, x0 + 130, y0 + 174)
	rounded_rectangle(draw, icon_back, 10, (11, 12, 17, 255), outline=(73, 75, 89), width=2)
	sprite = first_frame(focus.texture).resize((96, 96), Image.Resampling.NEAREST)
	canvas.alpha_composite(sprite, (x0 + 26, y0 + 70))

	draw.text((x0 + 18, y0 + 15), focus.name, font=FONT_CARD_TITLE, fill=(252, 252, 255))

	chip_x = x0 + 148
	chip_y = y0 + 68
	chip_x = chip(draw, chip_x, chip_y, AFFINITY_LABELS[focus.affinity], accent, text_fill=(16, 16, 18) if focus.affinity == "holy" else (250, 250, 252))
	chip(draw, chip_x, chip_y, f"Cost {focus.cost}", (82, 84, 100))

	draw.text((x0 + 148, y0 + 108), "Faction", font=FONT_LABEL, fill=(180, 184, 197))
	draw_text(draw, (x0 + 148, y0 + 131), focus.faction, FONT_BODY_BOLD, (238, 239, 244), x1 - x0 - 166, line_spacing=2, max_lines=2)

	section_y = y0 + 190
	draw.text((x0 + 18, section_y), "Lore", font=FONT_LABEL, fill=(*accent, 255))
	section_y += 24
	draw_text(draw, (x0 + 18, section_y), focus.lore[0], FONT_BODY, (221, 220, 228), x1 - x0 - 36, line_spacing=3, max_lines=2)
	section_y += 50
	draw_text(draw, (x0 + 18, section_y), focus.lore[1], FONT_BODY, (221, 220, 228), x1 - x0 - 36, line_spacing=3, max_lines=2)

	effect_y = y1 - 106
	draw.line((x0 + 18, effect_y - 14, x1 - 18, effect_y - 14), fill=(64, 66, 78), width=1)
	draw.text((x0 + 18, effect_y), "What It Does", font=FONT_LABEL, fill=(151, 217, 150))
	draw_text(draw, (x0 + 18, effect_y + 24), focus.effect, FONT_SMALL, (232, 236, 224), x1 - x0 - 36, line_spacing=2, max_lines=3)


def panel_filename(title: str) -> str:
	return "attuned-" + title.lower().replace(" ", "-").replace("/", "-") + ".png"


def render_panel(title: str, subtitle: str, foci: list[Focus]) -> Path:
	accent = AFFINITY_COLORS[foci[0].affinity] if foci else (170, 170, 180)
	canvas = make_background(accent)
	draw = ImageDraw.Draw(canvas)

	draw.rectangle((0, 0, WIDTH, 120), fill=(9, 10, 15, 218))
	draw.text((64, 30), title, font=FONT_TITLE, fill=(255, 255, 255))
	draw.text((66, 90), subtitle, font=FONT_SUBTITLE, fill=(199, 203, 214))
	draw.text((WIDTH - 410, 39), "Actual in-game Focus assets", font=FONT_BODY_BOLD, fill=(234, 235, 242))
	draw.text((WIDTH - 410, 68), "Lore, effect, affinity, faction, cost", font=FONT_SMALL, fill=(174, 178, 190))

	cols = 5
	rows = math.ceil(len(foci) / cols)
	margin_x = 54
	gap_x = 18
	gap_y = 18
	top = 150
	card_w = (WIDTH - margin_x * 2 - gap_x * (cols - 1)) // cols
	card_h = (HEIGHT - top - 54 - gap_y * (rows - 1)) // rows
	for idx, focus in enumerate(foci):
		col = idx % cols
		row = idx // cols
		x0 = margin_x + col * (card_w + gap_x)
		y0 = top + row * (card_h + gap_y)
		draw_focus_card(canvas, focus, (x0, y0, x0 + card_w, y0 + card_h))

	OUT_DIR.mkdir(parents=True, exist_ok=True)
	out = OUT_DIR / panel_filename(title)
	canvas.convert("RGB").save(out, quality=96)
	return out


def render_all_foci_index(foci: list[Focus]) -> Path:
	canvas = make_background((160, 91, 224))
	draw = ImageDraw.Draw(canvas)
	draw.rectangle((0, 0, WIDTH, 126), fill=(9, 10, 15, 225))
	draw.text((64, 28), "Attuned Focus Collection", font=FONT_TITLE, fill=(255, 255, 255))
	draw.text((66, 89), f"All {len(foci)} Foci, copied directly from the mod's shipped item textures.", font=FONT_SUBTITLE, fill=(203, 207, 218))

	cols = 16
	card = 104
	gap_x = 10
	gap_y = 16
	start_x = (WIDTH - cols * card - (cols - 1) * gap_x) // 2
	start_y = 150
	for idx, focus in enumerate(foci):
		col = idx % cols
		row = idx // cols
		x = start_x + col * (card + gap_x)
		y = start_y + row * (card + gap_y)
		accent = AFFINITY_COLORS[focus.affinity]
		rounded_rectangle(draw, (x, y, x + card, y + card), 10, (25, 27, 36, 236), outline=(*accent, 220), width=2)
		sprite = first_frame(focus.texture).resize((50, 50), Image.Resampling.NEAREST)
		canvas.alpha_composite(sprite, (x + 27, y + 8))
		name_lines = textwrap.wrap(focus.name.replace(" Focus", ""), width=12)
		text_y = y + 61
		for line in name_lines[:2]:
			w = draw.textlength(line, font=FONT_LABEL)
			draw.text((x + (card - w) / 2, text_y), line, font=FONT_LABEL, fill=(238, 239, 244))
			text_y += 16
		aff = AFFINITY_LABELS[focus.affinity]
		w = draw.textlength(aff, font=FONT_LABEL)
		draw.text((x + (card - w) / 2, y + card - 18), aff, font=FONT_LABEL, fill=accent)

	OUT_DIR.mkdir(parents=True, exist_ok=True)
	out = OUT_DIR / "attuned-all-foci-real-assets.png"
	canvas.convert("RGB").save(out, quality=96)
	return out


def draw_gallery_asset_card(canvas: Image.Image, asset: GalleryAsset, box: tuple[int, int, int, int]) -> None:
	draw = ImageDraw.Draw(canvas)
	x0, y0, x1, y1 = box
	accent = (151, 217, 150) if asset.kind == "Item" else (105, 185, 235)
	rounded_rectangle(draw, box, 12, (25, 27, 36, 235), outline=(*accent, 230), width=2)
	draw.rectangle((x0 + 2, y0 + 2, x1 - 2, y0 + 48), fill=(*accent, 38))
	draw.text((x0 + 18, y0 + 15), asset.name, font=FONT_CARD_TITLE, fill=(252, 252, 255))

	icon_back = (x0 + 22, y0 + 72, x0 + 138, y0 + 188)
	rounded_rectangle(draw, icon_back, 10, (11, 12, 17, 255), outline=(73, 75, 89), width=2)
	sprite = first_frame(asset.texture).resize((88, 88), Image.Resampling.NEAREST)
	canvas.alpha_composite(sprite, (x0 + 36, y0 + 86))

	chip(draw, x0 + 156, y0 + 78, asset.kind, accent, text_fill=(16, 16, 18))
	draw_text(draw, (x0 + 156, y0 + 122), asset.description, FONT_BODY, (226, 228, 236), x1 - x0 - 180, line_spacing=4, max_lines=4)


def render_items_and_altars_panel(assets: list[GalleryAsset]) -> Path:
	canvas = make_background((126, 173, 223))
	draw = ImageDraw.Draw(canvas)
	draw.rectangle((0, 0, WIDTH, 126), fill=(9, 10, 15, 225))
	draw.text((64, 28), "Items, Tools & Altars", font=FONT_TITLE, fill=(255, 255, 255))
	draw.text((66, 89), "Shipped item textures, custom Focus shells, tridents, reliquaries, shards, and affinity altar variants.", font=FONT_SUBTITLE, fill=(203, 207, 218))

	cols = 5
	rows = math.ceil(len(assets) / cols)
	margin_x = 54
	gap_x = 18
	gap_y = 18
	top = 150
	card_w = (WIDTH - margin_x * 2 - gap_x * (cols - 1)) // cols
	card_h = (HEIGHT - top - 54 - gap_y * (rows - 1)) // rows
	for idx, asset in enumerate(assets):
		col = idx % cols
		row = idx // cols
		x0 = margin_x + col * (card_w + gap_x)
		y0 = top + row * (card_h + gap_y)
		draw_gallery_asset_card(canvas, asset, (x0, y0, x0 + card_w, y0 + card_h))

	OUT_DIR.mkdir(parents=True, exist_ok=True)
	out = OUT_DIR / "attuned-items-tools-and-altars.png"
	canvas.convert("RGB").save(out, quality=96)
	return out


def load_hud_sprite(name: str, size: int = 92) -> Image.Image:
	path = HUD_TEXTURE_DIR / name
	if not path.exists():
		raise FileNotFoundError(path)
	return Image.open(path).convert("RGBA").resize((size, size), Image.Resampling.NEAREST)


def focus_by_id(foci: list[Focus], item_id: str) -> Focus:
	for focus in foci:
		if focus.id == item_id:
			return focus
	raise KeyError(item_id)


def draw_loadout(canvas: Image.Image, foci: list[Focus], x: int, y: int, icon_size: int = 42) -> None:
	draw = ImageDraw.Draw(canvas)
	for index, focus in enumerate(foci):
		ix = x + index * (icon_size + 10)
		rounded_rectangle(draw, (ix, y, ix + icon_size + 12, y + icon_size + 12), 7, (11, 12, 17, 245), outline=(74, 77, 91), width=1)
		sprite = first_frame(focus.texture).resize((icon_size, icon_size), Image.Resampling.NEAREST)
		canvas.alpha_composite(sprite, (ix + 6, y + 6))


def draw_apex_card(
	canvas: Image.Image,
	box: tuple[int, int, int, int],
	sprite_name: str,
	title: str,
	subtitle: str,
	body: str,
	loadout: list[Focus],
	accent: tuple[int, int, int],
	note: str | None = None,
) -> None:
	draw = ImageDraw.Draw(canvas)
	x0, y0, x1, y1 = box
	rounded_rectangle(draw, box, 14, (25, 27, 36, 238), outline=(*accent, 225), width=2)
	draw.rectangle((x0 + 2, y0 + 2, x1 - 2, y0 + 58), fill=(*accent, 45))
	draw.text((x0 + 22, y0 + 17), title, font=FONT_APEX_TITLE, fill=(255, 255, 255))
	icon_box = (x0 + 24, y0 + 82, x0 + 140, y0 + 198)
	rounded_rectangle(draw, icon_box, 12, (9, 10, 15, 255), outline=(82, 85, 100), width=2)
	canvas.alpha_composite(load_hud_sprite(sprite_name, 96), (x0 + 34, y0 + 92))
	draw.text((x0 + 160, y0 + 86), subtitle, font=FONT_BODY_BOLD, fill=(*accent, 255))
	body_lines = 4 if (y1 - y0) <= 340 else 5
	draw_text(draw, (x0 + 160, y0 + 116), body, FONT_APEX_BODY, (226, 228, 236), x1 - x0 - 186, line_spacing=5, max_lines=body_lines)
	draw.text((x0 + 24, y1 - 106), "Example real Foci", font=FONT_LABEL, fill=(183, 187, 199))
	draw_loadout(canvas, loadout, x0 + 24, y1 - 76)
	if note:
		draw_text(draw, (x0 + 278, y1 - 82), note, FONT_APEX_SMALL, (215, 216, 224), x1 - x0 - 302, line_spacing=3, max_lines=3)


def render_apex_stances_panel(foci: list[Focus]) -> Path:
	canvas = make_background((180, 83, 214))
	draw = ImageDraw.Draw(canvas)
	draw.rectangle((0, 0, WIDTH, 134), fill=(9, 10, 15, 226))
	draw.text((64, 24), "Apex, Discord & Neutral", font=FONT_TITLE, fill=(255, 255, 255))
	draw.text((66, 86), "Current release behavior, shown with real HUD sprites and real Focus assets.", font=FONT_SUBTITLE, fill=(202, 206, 218))
	draw.text((WIDTH - 512, 38), "Apex gate: near-full Focus build", font=FONT_BODY_BOLD, fill=(236, 237, 244))
	draw.text((WIDTH - 512, 68), "Near full capacity + combat Resonance", font=FONT_SMALL, fill=(175, 179, 192))
	draw.text((WIDTH - 512, 92), "Neutral Foci block committed Apex", font=FONT_SMALL, fill=(175, 179, 192))

	apex_cards = [
		(
			"execute.png",
			"Execute",
			"Fury Apex",
			"Finish low-health foes. Stronger against Bastion; neutralized when countered.",
			["edge_focus", "frenzy_focus", "cinder_focus", "bloodfury_focus"],
			AFFINITY_COLORS["fury"],
		),
		(
			"unyielding.png",
			"Unyielding",
			"Bastion Apex",
			"Caps incoming blows and ignores knockback. Built for holding ground.",
			["iron_focus", "bulwark_focus", "aegis_focus", "vital_focus"],
			AFFINITY_COLORS["bastion"],
		),
		(
			"untouchable.png",
			"Untouchable",
			"Zephyr Apex",
			"Dodge attacks while sprinting. Strongest when momentum stays alive.",
			["swift_focus", "leap_focus", "stormcall_focus", "tide_focus"],
			AFFINITY_COLORS["zephyr"],
		),
		(
			"judgment.png",
			"Judgment",
			"Holy Apex",
			"Marks wounded Fury-aligned foes for a decisive strike.",
			["votive_focus", "oathguard_focus", "sunlance_focus", "threshold_focus"],
			AFFINITY_COLORS["holy"],
		),
	]

	card_w = 424
	card_h = 326
	start_x = 54
	gap = 28
	for index, (sprite, title, subtitle, body, ids, accent) in enumerate(apex_cards):
		x = start_x + index * (card_w + gap)
		draw_apex_card(
			canvas,
			(x, 164, x + card_w, 164 + card_h),
			sprite,
			title,
			subtitle,
			body,
			[focus_by_id(foci, item_id) for item_id in ids],
			accent,
		)

	stance_cards = [
		(
			"maelstrom.png",
			"Maelstrom Apex",
			"Discord capstone",
			"Run every shipped affinity near full capacity. At resonance, hits deal +10% direct melee damage; the Apex briefly scrambles affinity foes.",
			["edge_focus", "iron_focus", "swift_focus", "sunlance_focus"],
			(221, 92, 230),
			"Requires every shipped affinity active. Neutral Foci may join if the build stays near full capacity.",
		),
		(
			"stillpoint.png",
			"Stillpoint Apex",
			"Neutral capstone",
			"Run only neutral active Foci near full capacity. At resonance, it suppresses affinity pressure and uses Absorption pulses.",
			["linecast_focus", "harborlight_focus", "driftglass_focus", "netmender_focus"],
			AFFINITY_COLORS["neutral"],
			"Requires every active Focus to be neutral. Any active affinity Focus breaks the pattern.",
		),
	]
	wide_w = 878
	for index, (sprite, title, subtitle, body, ids, accent, note) in enumerate(stance_cards):
		x = 54 + index * (wide_w + 56)
		draw_apex_card(
			canvas,
			(x, 548, x + wide_w, 548 + 388),
			sprite,
			title,
			subtitle,
			body,
			[focus_by_id(foci, item_id) for item_id in ids],
			accent,
			note,
		)

	draw.rectangle((54, 974, WIDTH - 54, 1032), fill=(10, 11, 16, 220), outline=(64, 66, 78))
	draw.text((76, 992), "Maelstrom and Stillpoint use shipped HUD sprites and real Focus assets in the current release.", font=FONT_BODY_BOLD, fill=(240, 223, 176))

	OUT_DIR.mkdir(parents=True, exist_ok=True)
	out = OUT_DIR / "attuned-apex-discord-neutral.png"
	canvas.convert("RGB").save(out, quality=96)
	return out


def main() -> None:
	foci = load_foci()
	gallery_assets = load_gallery_assets()
	by_affinity: dict[str, list[Focus]] = {}
	for focus in foci:
		by_affinity.setdefault(focus.affinity, []).append(focus)
	for group in by_affinity.values():
		group.sort(key=lambda focus: (focus.faction, focus.name))

	outputs = [
		render_all_foci_index(sorted(foci, key=lambda focus: focus.name)),
		render_apex_stances_panel(foci),
	]
	for affinity, title, subtitle in PANEL_ORDER:
		outputs.append(render_panel(title, subtitle, by_affinity[affinity]))

	umbral = by_affinity["umbral"]
	outputs.append(render_panel("Umbral Foci I", "Shadow, smoke, debt, dread, and ambush relics for the first half of the Umbral lane.", umbral[:9]))
	outputs.append(render_panel("Umbral Foci II", "Null, snare, rite, eclipse, and low-light relics for the second half of the Umbral lane.", umbral[9:]))

	neutral = by_affinity["neutral"]
	outputs.append(render_panel("Neutral Foci I", "Utility, exploration, farming, mining, and survival relics with no affinity lock.", neutral[:10]))
	outputs.append(render_panel("Neutral Foci II", "Seafarers, Unseen, Verdant, travel, and mixed-build utility relics.", neutral[10:]))
	outputs.append(render_items_and_altars_panel(gallery_assets))

	print("Generated Modrinth gallery panels:")
	for output in outputs:
		print(output)


if __name__ == "__main__":
	main()
