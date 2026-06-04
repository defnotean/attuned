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


if __name__ == "__main__":
    unittest.main()
