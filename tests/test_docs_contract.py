from __future__ import annotations

import json
import re
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

    def test_reference_docs_cover_discord_apex_and_shipped_behaviors(self) -> None:
        reference = (ROOT / "docs" / "reference.md").read_text(encoding="utf-8")
        readme = (ROOT / "docs" / "README.md").read_text(encoding="utf-8")

        self.assertNotIn("cannot reach Apex", reference)
        self.assertIn("Maelstrom", reference)
        self.assertIn("Stillpoint", reference)
        self.assertNotIn("must share one affinity", readme)
        self.assertIn("Discord", readme)

        focus_dir = ROOT / "src" / "main" / "resources" / "data" / "attuned" / "attuned" / "focus"
        behavior_ids = sorted(
            json.loads(path.read_text(encoding="utf-8"))["behavior"]
            for path in focus_dir.glob("*.json")
            if "behavior" in json.loads(path.read_text(encoding="utf-8"))
        )
        for behavior_id in behavior_ids:
            self.assertIn(behavior_id, reference)

    def test_reference_docs_cover_every_server_config_key(self) -> None:
        config_source = (ROOT / "src" / "main" / "java" / "dev" / "attuned" / "AttunedConfig.java").read_text(
            encoding="utf-8"
        )
        reference = (ROOT / "docs" / "reference.md").read_text(encoding="utf-8")

        config_keys = sorted(set(re.findall(r'optionalFieldOf\("([^"]+)"', config_source)))

        self.assertGreater(len(config_keys), 0)
        for key in config_keys:
            self.assertIn(f"`{key}`", reference, f"docs/reference.md should document config key {key}")

    def test_reference_docs_describe_registry_authoritative_focus_loot(self) -> None:
        reference = (ROOT / "docs" / "reference.md").read_text(encoding="utf-8")

        self.assertIn("FocusDefinition data", reference)
        self.assertNotIn("AttunedContent.FOCI", reference)

    def test_contributing_describes_current_version_release_notes(self) -> None:
        contributing = (ROOT / "CONTRIBUTING.md").read_text(encoding="utf-8")

        self.assertIn("## Attuned <mod_version>", contributing)
        self.assertIn("current-version section", contributing)


if __name__ == "__main__":
    unittest.main()
