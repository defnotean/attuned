from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate_modrinth_gallery.py"


class ModrinthGalleryContractTest(unittest.TestCase):
	def setUp(self) -> None:
		self.source = GENERATOR.read_text(encoding="utf-8")

	def test_apex_panel_uses_shipped_capstone_sprites(self) -> None:
		for sprite in ("execute.png", "unyielding.png", "untouchable.png", "judgment.png", "maelstrom.png", "stillpoint.png"):
			self.assertIn(sprite, self.source)
		for stale_sprite in ("affinity_fury.png", "affinity_bastion.png", "affinity_zephyr.png", "affinity_holy.png"):
			self.assertNotIn(stale_sprite, self.source)

	def test_maelstrom_and_stillpoint_copy_matches_shipped_behavior(self) -> None:
		self.assertIn("+10% direct melee damage", self.source)
		self.assertIn("Requires every shipped affinity active", self.source)
		self.assertIn("briefly scrambles", self.source)
		self.assertIn("Requires every active Focus to be neutral", self.source)
		self.assertIn("suppresses affinity pressure", self.source)
		self.assertIn("uses Absorption pulses", self.source)

	def test_gallery_covers_all_eight_affinities_and_item_assets(self) -> None:
		for affinity in ("fury", "bastion", "zephyr", "holy", "tide", "forge", "verdant", "umbral"):
			self.assertIn(f'"{affinity}"', self.source)
		for panel in (
			"Tide Foci",
			"Forge Foci",
			"Verdant Foci",
			"Umbral Foci I",
			"Umbral Foci II",
			"Items, Tools & Altars",
		):
			self.assertIn(panel, self.source)
		self.assertIn("attuned-items-tools-and-altars.png", self.source)
		self.assertIn("attunement_altar_top_tide", self.source)
		self.assertIn("attunement_altar_top_forge", self.source)
		self.assertIn("attunement_altar_top_verdant", self.source)
		self.assertIn("attunement_altar_top_umbral", self.source)


if __name__ == "__main__":
	unittest.main()
