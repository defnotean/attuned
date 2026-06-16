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

    def test_default_development_branch_is_latest(self) -> None:
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        migration = (ROOT / "docs" / "versioning" / "minecraft-version-migration.md").read_text(encoding="utf-8")

        self.assertIn("branches: [latest]", ci)
        self.assertNotIn("branches: [main]", ci)
        self.assertIn("tools/minecraft_version_profile.py current --github-output", ci)
        self.assertIn("steps.versions.outputs.java_version", ci)
        self.assertIn("Start from green `latest`", migration)
        self.assertNotIn("git checkout main", migration)

    def test_supported_branch_docs_cover_version_matrix(self) -> None:
        matrix = (ROOT / "docs" / "versioning" / "supported-branches.md").read_text(encoding="utf-8")

        expected_rows = {
            "| `latest` | `26.1.2` | current |",
            "| `maintenance/minecraft-1.21.11` | `1.21.11` | maintenance |",
            "| `maintenance/minecraft-1.20.6` | `1.20.6` | maintenance |",
            "| `maintenance/minecraft-1.19.4` | `1.19.4` | maintenance |",
            "| `maintenance/minecraft-1.18.2` | `1.18.2` | maintenance |",
        }
        for row in expected_rows:
            self.assertIn(row, matrix)

    def test_public_docs_keep_asset_workflow_private(self) -> None:
        public_docs = [
            ROOT / "README.md",
            ROOT / "docs" / "README.md",
            ROOT / "docs" / "reference.md",
            ROOT / "docs" / "adding-a-focus.md",
            ROOT / "docs" / "authoring-foci.md",
            ROOT / "docs" / "platform" / "modrinth-description.md",
            ROOT / "docs" / "platform" / "curseforge-description.md",
        ]
        forbidden = re.compile(r"image-generation|provenance|generated image", re.IGNORECASE)

        leaks = []
        for doc in public_docs:
            text = doc.read_text(encoding="utf-8")
            for match in forbidden.finditer(text):
                leaks.append(f"{doc.relative_to(ROOT)}:{text[:match.start()].count(chr(10)) + 1}:{match.group(0)}")

        self.assertEqual([], leaks, "Public docs should describe art neutrally and keep asset workflow details private")


if __name__ == "__main__":
    unittest.main()
