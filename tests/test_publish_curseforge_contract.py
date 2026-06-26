from __future__ import annotations

import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "publish_curseforge.py"

spec = importlib.util.spec_from_file_location("publish_curseforge", MODULE_PATH)
publish_curseforge = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(publish_curseforge)


class PublishCurseForgeTest(unittest.TestCase):
    def test_metadata_uses_release_changelog_and_full_game_version_names(self):
        changelog = """# Changelog

## Attuned 1.3.0 - The Focus Reliquary

- Added the reliquary.

## Attuned 1.2.7 - Older

- Older notes.
"""
        metadata = publish_curseforge.build_metadata(
            changelog=changelog,
            version="1.3.0",
            minecraft_version="26.2",
            java_version="25",
        )

        self.assertEqual("Attuned 1.3.0", metadata["displayName"])
        self.assertEqual("markdown", metadata["changelogType"])
        self.assertEqual("release", metadata["releaseType"])
        self.assertIn("## Attuned 1.3.0 - The Focus Reliquary", metadata["changelog"])
        self.assertNotIn("Attuned 1.2.7", metadata["changelog"])
        self.assertEqual(["26.2", "Java 25", "Client", "Server", "Fabric"],
                         metadata["gameVersionNames"])
        self.assertEqual(
            {"projects": [{"slug": "fabric-api", "type": "requiredDependency"}]},
            metadata["relations"],
        )

    def test_metadata_can_target_non_fabric_loader_without_fabric_dependency(self):
        changelog = """# Changelog

## Attuned 1.3.0 - The Focus Reliquary

- Added the reliquary.
"""
        metadata = publish_curseforge.build_metadata(
            changelog=changelog,
            version="1.3.0",
            minecraft_version="1.20.1",
            java_version="17",
            loader="forge",
        )

        self.assertEqual(["1.20.1", "Java 17", "Client", "Server", "Forge"],
                         metadata["gameVersionNames"])
        self.assertEqual({"projects": []}, metadata["relations"])
        self.assertNotIn("Fabric", metadata["gameVersionNames"])

    def test_metadata_allows_loader_specific_required_dependencies(self):
        changelog = """# Changelog

## Attuned 1.3.0 - The Focus Reliquary

- Added the reliquary.
"""
        metadata = publish_curseforge.build_metadata(
            changelog=changelog,
            version="1.3.0",
            minecraft_version="26.1",
            java_version="25",
            loader="neoforge",
            required_dependency_slugs=("loader-api-example",),
        )

        self.assertEqual("NeoForge", metadata["gameVersionNames"][-1])
        self.assertEqual(
            {"projects": [{"slug": "loader-api-example", "type": "requiredDependency"}]},
            metadata["relations"],
        )

    def test_multipart_body_contains_metadata_and_jar_file(self):
        metadata = {"displayName": "Attuned 1.3.0", "releaseType": "release"}
        body, content_type = publish_curseforge.multipart_body(
            metadata=metadata,
            file_bytes=b"jar-bytes",
            filename="attuned-1.3.0.jar",
            boundary="test-boundary",
        )

        self.assertEqual("multipart/form-data; boundary=test-boundary", content_type)
        self.assertIn(b'name="metadata"', body)
        self.assertIn(json.dumps(metadata, separators=(",", ":")).encode("utf-8"), body)
        self.assertIn(b'name="file"; filename="attuned-1.3.0.jar"', body)
        self.assertIn(b"jar-bytes", body)
        self.assertTrue(body.endswith(b"--test-boundary--\r\n"))


    def test_main_reads_java_version_from_gradle_properties(self):
        source = MODULE_PATH.read_text(encoding="utf-8")

        self.assertIn('java_version = props["java_version"]', source)
        self.assertNotIn('java_version="25"', source)
        self.assertNotIn('java_version = "25"', source)


if __name__ == "__main__":
    unittest.main()
