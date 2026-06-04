from __future__ import annotations

import json
import os
import py_compile
import re
import struct
import sys
import tempfile
import zlib
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
SRC_ROOT = ROOT / "src"
PNG_RESOURCE_ROOT = SRC_ROOT / "main" / "resources"
PYTHON_ROOTS = (ROOT / "tools", ROOT / "tests")
SOURCE_SCAN_ROOTS = (ROOT / "src", ROOT / "tools", ROOT / "tests", ROOT / ".github")

SOURCE_SUFFIXES = {
    ".accesswidener",
    ".css",
    ".gradle",
    ".html",
    ".java",
    ".js",
    ".json",
    ".md",
    ".properties",
    ".py",
    ".toml",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}
SOURCE_SKIP_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    ".vscode",
    "build",
    "node_modules",
    "out",
    "run",
    "__pycache__",
}
TRANSIENT_SKIP_DIRS = {".git"}
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_REASONABLE_PNG_DIMENSION = 8192
ISSUE_WORDS = ("TO" + "DO", "FIX" + "ME", "HA" + "CK")
NAME_PARTS = (
    "api" + "_key",
    "api" + "-key",
    "api" + "key",
    "access" + "_tok" + "en",
    "access" + "-tok" + "en",
    "auth" + "_tok" + "en",
    "auth" + "-tok" + "en",
    "client" + "_sec" + "ret",
    "client" + "-sec" + "ret",
    "sec" + "ret",
    "pass" + "word",
    "passwd",
    "cre" + "dential",
    "tok" + "en",
)
PLACEHOLDER_VALUES = {
    "",
    "0",
    "1",
    "changeme",
    "default",
    "dummy",
    "example",
    "false",
    "local",
    "localhost",
    "none",
    "null",
    "placeholder",
    "sample",
    "test",
    "true",
}
RISK_NAME_PATTERN = "|".join(re.escape(part) for part in NAME_PARTS)
ASSIGNMENT_PATTERN = re.compile(
    rf"\b[a-z0-9_.-]*(?:{RISK_NAME_PATTERN})[a-z0-9_.-]*\b\s*[:=]\s*(?P<value>[^\s,#}}]+)",
    re.IGNORECASE,
)


class CheckFailed(Exception):
    def __init__(self, title: str, problems: list[str]) -> None:
        super().__init__(title)
        self.title = title
        self.problems = problems


def relative(path: Path, root: Path = ROOT) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def iter_files(root: Path, skip_dirs: set[str]) -> Iterable[Path]:
    if not root.exists():
        return
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(name for name in dirnames if name not in skip_dirs)
        for filename in sorted(filenames):
            yield Path(dirpath) / filename


def source_files() -> list[Path]:
    files = [
        path
        for root in SOURCE_SCAN_ROOTS
        for path in iter_files(root, SOURCE_SKIP_DIRS)
        if path.suffix.lower() in SOURCE_SUFFIXES
    ]
    files.extend(
        path
        for path in ROOT.iterdir()
        if path.is_file() and (path.suffix.lower() in SOURCE_SUFFIXES or path.name in {"gradlew"})
    )
    return sorted(files)


def check_src_json() -> str:
    json_files = sorted(SRC_ROOT.rglob("*.json"))
    problems: list[str] = []
    for path in json_files:
        try:
            with path.open("r", encoding="utf-8") as handle:
                json.load(handle)
        except json.JSONDecodeError as exc:
            problems.append(f"{relative(path)}:{exc.lineno}:{exc.colno}: {exc.msg}")
        except OSError as exc:
            problems.append(f"{relative(path)}: {exc}")
    if problems:
        raise CheckFailed("src JSON parsing", problems)
    return f"src JSON parsing: {len(json_files)} files"


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        header = handle.read(33)
    if len(header) < 33:
        raise ValueError("file is shorter than a PNG signature and IHDR chunk")
    if header[:8] != PNG_SIGNATURE:
        raise ValueError("invalid PNG signature")
    length = struct.unpack(">I", header[8:12])[0]
    chunk_type = header[12:16]
    if length != 13 or chunk_type != b"IHDR":
        raise ValueError("first chunk is not a valid IHDR")
    ihdr = header[16:29]
    expected_crc = struct.unpack(">I", header[29:33])[0]
    actual_crc = zlib.crc32(chunk_type + ihdr) & 0xFFFFFFFF
    if expected_crc != actual_crc:
        raise ValueError("IHDR CRC mismatch")
    width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(">IIBBBBB", ihdr)
    if width <= 0 or height <= 0:
        raise ValueError(f"invalid dimensions {width}x{height}")
    if width > MAX_REASONABLE_PNG_DIMENSION or height > MAX_REASONABLE_PNG_DIMENSION:
        raise ValueError(f"unreasonable dimensions {width}x{height}")
    if bit_depth not in {1, 2, 4, 8, 16}:
        raise ValueError(f"invalid bit depth {bit_depth}")
    if color_type not in {0, 2, 3, 4, 6}:
        raise ValueError(f"invalid color type {color_type}")
    if compression != 0 or filter_method != 0 or interlace not in {0, 1}:
        raise ValueError("invalid IHDR encoding fields")
    return width, height


def check_png_resources() -> str:
    png_files = sorted(PNG_RESOURCE_ROOT.rglob("*.png"))
    problems: list[str] = []
    for path in png_files:
        try:
            png_dimensions(path)
        except (OSError, ValueError) as exc:
            problems.append(f"{relative(path)}: {exc}")
    if problems:
        raise CheckFailed("PNG resource headers", problems)
    return f"PNG resource headers: {len(png_files)} files"


def scan_issue_markers(paths: Iterable[Path], root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    for path in paths:
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            problems.append(f"{relative(path, root)}: cannot decode as UTF-8: {exc}")
            continue
        except OSError as exc:
            problems.append(f"{relative(path, root)}: {exc}")
            continue
        for line_number, line in enumerate(text.splitlines(), 1):
            upper = line.upper()
            for word in ISSUE_WORDS:
                if word in upper:
                    problems.append(f"{relative(path, root)}:{line_number}: remove tracked work marker")
                    break
    return problems


def check_issue_markers() -> str:
    files = source_files()
    problems = scan_issue_markers(files)
    if problems:
        raise CheckFailed("source marker scan", problems)
    return f"source marker scan: {len(files)} files"


def clean_value(raw_value: str) -> str:
    return raw_value.strip().strip("'\"").strip()


def is_placeholder_value(value: str) -> bool:
    lowered = value.lower()
    return (
        lowered in PLACEHOLDER_VALUES
        or "environmentvariable(" in lowered
        or "getenv(" in lowered
        or "os.environ" in lowered
        or lowered.startswith(("$", "${", "%", "<", "{{"))
        or lowered.endswith(("_here", "-here"))
    )


def scan_assignment_risks(paths: Iterable[Path], root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    for path in paths:
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for line_number, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if not stripped or stripped.startswith(("#", "//", "/*", "*", "<!--")):
                continue
            match = ASSIGNMENT_PATTERN.search(line)
            if not match:
                continue
            value = clean_value(match.group("value"))
            if is_placeholder_value(value):
                continue
            problems.append(f"{relative(path, root)}:{line_number}: potential sensitive assignment")
    return problems


def check_assignment_risks() -> str:
    files = source_files()
    problems = scan_assignment_risks(files)
    if problems:
        raise CheckFailed("sensitive assignment scan", problems)
    return f"sensitive assignment scan: {len(files)} files"


def check_python_caches() -> str:
    problems: list[str] = []
    scanned_files = 0
    for path in iter_files(ROOT, TRANSIENT_SKIP_DIRS):
        scanned_files += 1
        if path.suffix.lower() in {".pyc", ".pyo"}:
            problems.append(relative(path))
        if path.parent.name == "__pycache__":
            cache_dir = path.parent
            marker = relative(cache_dir)
            if marker not in problems:
                problems.append(marker)
    for dirpath, dirnames, _filenames in os.walk(ROOT):
        dirnames[:] = sorted(name for name in dirnames if name not in TRANSIENT_SKIP_DIRS)
        current = Path(dirpath)
        if current.name == "__pycache__":
            marker = relative(current)
            if marker not in problems:
                problems.append(marker)
    if problems:
        raise CheckFailed("Python transient cache scan", sorted(problems))
    return f"Python transient cache scan: {scanned_files} filesystem entries"


def check_python_compile() -> str:
    python_files = sorted(
        path
        for root in PYTHON_ROOTS
        if root.exists()
        for path in root.rglob("*.py")
        if "__pycache__" not in path.parts
    )
    problems: list[str] = []
    with tempfile.TemporaryDirectory(prefix="attuned-pycompile-") as temp_dir:
        temp_root = Path(temp_dir)
        for path in python_files:
            output_path = temp_root / path.relative_to(ROOT).with_suffix(".pyc")
            output_path.parent.mkdir(parents=True, exist_ok=True)
            try:
                py_compile.compile(str(path), cfile=str(output_path), doraise=True)
            except py_compile.PyCompileError as exc:
                problems.append(f"{relative(path)}: {exc.msg}")
            except OSError as exc:
                problems.append(f"{relative(path)}: {exc}")
    if problems:
        raise CheckFailed("Python syntax compilation", problems)
    return f"Python syntax compilation: {len(python_files)} files"


def run_checks() -> int:
    checks = (
        check_src_json,
        check_png_resources,
        check_issue_markers,
        check_assignment_risks,
        check_python_caches,
        check_python_compile,
    )
    summaries: list[str] = []
    failures: list[CheckFailed] = []
    for check in checks:
        try:
            summaries.append(check())
        except CheckFailed as exc:
            failures.append(exc)
    if failures:
        print("Repository validation failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure.title}", file=sys.stderr)
            for problem in failure.problems:
                print(f"  - {problem}", file=sys.stderr)
        return 1
    print("Repository validation passed.")
    for summary in summaries:
        print(f"- {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(run_checks())
