from __future__ import annotations

import importlib.util
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "verify_repository.py"

spec = importlib.util.spec_from_file_location("verify_repository", MODULE_PATH)
verify_repository = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(verify_repository)


def write_minimal_png(path: Path, width: int, height: int) -> None:
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    chunk = b"IHDR" + ihdr
    crc = zlib.crc32(chunk) & 0xFFFFFFFF
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + chunk + struct.pack(">I", crc))


def png_chunk(chunk_type: bytes, data: bytes = b"") -> bytes:
    chunk = chunk_type + data
    crc = zlib.crc32(chunk) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + chunk + struct.pack(">I", crc)


def write_complete_png(path: Path, width: int, height: int) -> None:
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", ihdr)
        + png_chunk(b"IDAT", zlib.compress(b""))
        + png_chunk(b"IEND")
    )


class VerifyRepositoryContractTest(unittest.TestCase):
    def test_png_ihdr_parser_returns_dimensions(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            png_path = Path(temp_dir) / "sample.png"
            write_minimal_png(png_path, 16, 32)

            self.assertEqual(verify_repository.png_dimensions(png_path), (16, 32))

    def test_source_marker_scan_reports_runtime_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            sample = Path(temp_dir) / "sample.py"
            marker = "TO" + "DO"
            sample.write_text(f"# {marker}: follow up\n", encoding="utf-8")

            problems = verify_repository.scan_issue_markers([sample], Path(temp_dir))

            self.assertEqual(len(problems), 1)
            self.assertIn("sample.py:1", problems[0])

    def test_source_marker_scan_ignores_marker_substrings_in_identifiers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            sample = Path(temp_dir) / "sample.json"
            # The vanilla worldgen key `use_expansion_hack` embeds a marker word
            # as a substring but is not itself a tracked work marker.
            sample.write_text('{ "use_expansion_hack": false }\n', encoding="utf-8")

            problems = verify_repository.scan_issue_markers([sample], Path(temp_dir))

            self.assertEqual(problems, [])

    def test_sensitive_assignment_scan_omits_assigned_value(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            sample = Path(temp_dir) / "sample.properties"
            key_name = "api" + "_key"
            assigned_value = "abc123456789"
            sample.write_text(f"{key_name} = {assigned_value}\n", encoding="utf-8")

            problems = verify_repository.scan_assignment_risks([sample], Path(temp_dir))

            self.assertEqual(len(problems), 1)
            self.assertIn("sample.properties:1", problems[0])
            self.assertNotIn(assigned_value, problems[0])

    def test_readme_focus_count_reports_catalog_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            focus_dir = root / "src" / "main" / "resources" / "data" / "attuned" / "attuned" / "focus"
            focus_dir.mkdir(parents=True)
            (focus_dir / "first_focus.json").write_text("{}", encoding="utf-8")
            (focus_dir / "second_focus.json").write_text("{}", encoding="utf-8")
            (root / "README.md").write_text("- 1 Foci across test categories\n", encoding="utf-8")

            problems = verify_repository.readme_focus_count_problems(root)

            self.assertEqual(len(problems), 1)
            self.assertIn("README.md", problems[0])
            self.assertIn("advertises 1 Foci", problems[0])
            self.assertIn("ships 2 FocusDefinition files", problems[0])

    def test_current_changelog_section_excludes_prior_release_notes(self) -> None:
        changelog = (
            "# Changelog\n\n"
            "## Attuned 1.2.7 - Current release\n\n"
            "- Current note\n\n"
            "## Attuned 1.2.6 - Prior release\n\n"
            "- Stale note\n"
        )

        section = verify_repository.current_changelog_section(changelog, "1.2.7")

        self.assertIn("Attuned 1.2.7", section)
        self.assertIn("Current note", section)
        self.assertNotIn("Attuned 1.2.6", section)
        self.assertNotIn("Stale note", section)

    def test_modrinth_changelog_problems_report_whole_file_uploads(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "gradle.properties").write_text("mod_version=1.2.7\n", encoding="utf-8")
            (root / "CHANGELOG.md").write_text(
                "# Changelog\n\n"
                "## Attuned 1.2.7 - Current release\n\n"
                "- Current note\n\n"
                "## Attuned 1.2.6 - Prior release\n\n"
                "- Stale note\n",
                encoding="utf-8",
            )
            (root / "build.gradle").write_text(
                "modrinth {\n"
                "    changelog = providers.fileContents(layout.projectDirectory.file(\"CHANGELOG.md\")).asText.get()\n"
                "}\n",
                encoding="utf-8",
            )

            problems = verify_repository.modrinth_changelog_problems(root)

            self.assertEqual(len(problems), 1)
            self.assertIn("current Attuned 1.2.7 changelog section", problems[0])

    def test_gradle_changelog_match_uses_version_boundary(self) -> None:
        build_gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")

        self.assertIn("it == headingPrefix || it.startsWith(headingPrefix + \" \")", build_gradle)
        self.assertNotIn("it.startsWith(headingPrefix) }", build_gradle)

    def test_modrinth_gallery_pngs_report_missing_or_wrong_sized_panels(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            gallery = root / "docs" / "modrinth-gallery"
            gallery.mkdir(parents=True)
            for name in verify_repository.EXPECTED_MODRINTH_GALLERY_PNGS:
                write_complete_png(gallery / name, 1920, 1080)
            write_complete_png(gallery / "attuned-zephyr-foci.png", 1280, 720)
            (gallery / "attuned-holy-foci.png").unlink()

            problems = verify_repository.modrinth_gallery_png_problems(root)

            self.assertEqual(len(problems), 2)
            self.assertTrue(any("attuned-zephyr-foci.png" in problem and "1280x720" in problem for problem in problems))
            self.assertTrue(any("attuned-holy-foci.png" in problem and "missing" in problem for problem in problems))

    def test_modrinth_gallery_pngs_report_truncated_panels(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            gallery = root / "docs" / "modrinth-gallery"
            gallery.mkdir(parents=True)
            for name in verify_repository.EXPECTED_MODRINTH_GALLERY_PNGS:
                write_complete_png(gallery / name, 1920, 1080)
            write_minimal_png(gallery / "attuned-zephyr-foci.png", 1920, 1080)

            problems = verify_repository.modrinth_gallery_png_problems(root)

            self.assertEqual(len(problems), 1)
            self.assertIn("attuned-zephyr-foci.png", problems[0])
            self.assertIn("IDAT", problems[0])


if __name__ == "__main__":
    unittest.main()
