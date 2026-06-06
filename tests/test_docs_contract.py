from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class DocsContractTest(unittest.TestCase):
    def test_adding_focus_guide_matches_current_focus_api(self) -> None:
        guide = (ROOT / "docs" / "adding-a-focus.md").read_text(encoding="utf-8")

        self.assertIn('registerFocus("stoneskin_focus")', guide)
        self.assertNotIn('register("stoneskin_focus")', guide)
        self.assertNotIn("Find the `FOCI` list", guide)
        self.assertNotIn("one `FOCI` entry", guide)
        self.assertIn("AttunedFocusBehaviors.java", guide)
        self.assertIn("boolean onAbility(ServerPlayer player, ItemStack focus)", guide)
        self.assertIn("hasActiveAbility()", guide)
        self.assertIn("abilityCooldownTicks()", guide)


if __name__ == "__main__":
    unittest.main()
