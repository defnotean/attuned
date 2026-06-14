from __future__ import annotations

import json
import os
import py_compile
import re
import struct
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
SRC_ROOT = ROOT / "src"
PNG_RESOURCE_ROOT = SRC_ROOT / "main" / "resources"
MODRINTH_GALLERY_RELATIVE_DIR = Path("docs/modrinth-gallery")
PYTHON_ROOTS = (ROOT / "tools", ROOT / "tests")
SOURCE_SCAN_ROOTS = (ROOT / "src", ROOT / "tools", ROOT / "tests", ROOT / ".github")
FOCUS_DEFINITION_RELATIVE_DIR = Path("src/main/resources/data/attuned/attuned/focus")
GRADLE_PROPERTIES_RELATIVE_FILE = Path("gradle.properties")
BUILD_GRADLE_RELATIVE_FILE = Path("build.gradle")
CHANGELOG_RELATIVE_FILE = Path("CHANGELOG.md")
EXPECTED_MODRINTH_GALLERY_PNGS = (
    "attuned-all-foci-real-assets.png",
    "attuned-apex-discord-neutral.png",
    "attuned-bastion-foci.png",
    "attuned-fury-foci.png",
    "attuned-holy-foci.png",
    "attuned-neutral-foci-i.png",
    "attuned-neutral-foci-ii.png",
    "attuned-zephyr-foci.png",
)
MODRINTH_GALLERY_SIZE = (1920, 1080)

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
TRANSIENT_SKIP_DIRS = {
    ".git",
    # Heavy generated trees that can never ship; scanning them only slows the
    # gate down. Cache files in scanned dirs are still filtered through git's
    # ignore rules so locally-ignored __pycache__ from a pytest run never fails
    # the gate, while a cache that git would actually pick up still does.
    ".gradle",
    ".codex-remote-attachments",
    ".superpowers",
    "build",
    "out",
    "run",
    "tmp",
}
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_REASONABLE_PNG_DIMENSION = 8192
ISSUE_WORDS = ("TO" + "DO", "FIX" + "ME", "HA" + "CK")
ISSUE_MARKER_PATTERN = re.compile(
    r"\b(?:" + "|".join(re.escape(word) for word in ISSUE_WORDS) + r")\b",
    re.IGNORECASE,
)
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
README_FOCI_PATTERN = re.compile(r"\b(?P<count>\d+)\s+Foci\b")
CHANGELOG_HEADING_PATTERN = re.compile(r"^##\s+Attuned\s+(?P<version>\S+)\b", re.MULTILINE)
MODRINTH_CHANGELOG_PROVIDER_PATTERN = re.compile(
    r"^\s*changelog\s*=\s*providers\.provider\s*\{[^}]*currentChangelogSection\([^}]*\}\.get\(\)",
    re.MULTILINE | re.DOTALL,
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


def validate_png_chunks(path: Path) -> None:
    seen_idat = False
    seen_iend = False
    with path.open("rb") as handle:
        signature = handle.read(8)
        if signature != PNG_SIGNATURE:
            raise ValueError("invalid PNG signature")
        while True:
            length_bytes = handle.read(4)
            if not length_bytes:
                break
            if len(length_bytes) != 4:
                raise ValueError("truncated PNG chunk length")
            length = struct.unpack(">I", length_bytes)[0]
            chunk_type = handle.read(4)
            data = handle.read(length)
            crc_bytes = handle.read(4)
            if len(chunk_type) != 4 or len(data) != length or len(crc_bytes) != 4:
                raise ValueError("truncated PNG chunk")
            expected_crc = struct.unpack(">I", crc_bytes)[0]
            actual_crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
            if expected_crc != actual_crc:
                raise ValueError(f"{chunk_type.decode('ascii', errors='replace')} CRC mismatch")
            if chunk_type == b"IDAT":
                seen_idat = True
            elif chunk_type == b"IEND":
                seen_iend = True
                break
        if not seen_idat:
            raise ValueError("missing IDAT chunk")
        if not seen_iend:
            raise ValueError("missing IEND chunk")


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


def modrinth_gallery_png_problems(root: Path = ROOT) -> list[str]:
    gallery = root / MODRINTH_GALLERY_RELATIVE_DIR
    problems: list[str] = []
    for name in EXPECTED_MODRINTH_GALLERY_PNGS:
        path = gallery / name
        if not path.is_file():
            problems.append(f"{relative(path, root)}: missing expected gallery PNG")
            continue
        try:
            dimensions = png_dimensions(path)
            validate_png_chunks(path)
        except (OSError, ValueError) as exc:
            problems.append(f"{relative(path, root)}: {exc}")
            continue
        if dimensions != MODRINTH_GALLERY_SIZE:
            width, height = dimensions
            expected_width, expected_height = MODRINTH_GALLERY_SIZE
            problems.append(
                f"{relative(path, root)}: expected {expected_width}x{expected_height}, found {width}x{height}"
            )
    return problems


def check_modrinth_gallery_pngs() -> str:
    problems = modrinth_gallery_png_problems()
    if problems:
        raise CheckFailed("Modrinth gallery PNGs", problems)
    return f"Modrinth gallery PNGs: {len(EXPECTED_MODRINTH_GALLERY_PNGS)} files"


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
            # Match whole-word markers only, so legitimate identifiers that merely
            # contain a marker substring (e.g. the vanilla worldgen key
            # `use_expansion_hack`) are not flagged.
            if ISSUE_MARKER_PATTERN.search(line):
                problems.append(f"{relative(path, root)}:{line_number}: remove tracked work marker")
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


def git_ignored_paths(candidates: list[Path]) -> set[str]:
    """Return the subset of candidate paths (as repo-relative posix strings)
    that git ignores. Falls back to an empty set when git is unavailable so the
    cache check stays conservative."""
    if not candidates:
        return set()
    relative_paths = [relative(path) for path in candidates]
    try:
        # NUL-separated mode avoids Windows newline translation mangling paths.
        result = subprocess.run(
            ["git", "-C", str(ROOT), "check-ignore", "--stdin", "-z"],
            input="\0".join(relative_paths).encode("utf-8"),
            capture_output=True,
            check=False,
        )
    except OSError:
        return set()
    return {
        entry.decode("utf-8")
        for entry in result.stdout.split(b"\0")
        if entry
    }


def check_python_caches() -> str:
    candidates: list[Path] = []
    scanned_files = 0
    for path in iter_files(ROOT, TRANSIENT_SKIP_DIRS):
        scanned_files += 1
        if path.suffix.lower() in {".pyc", ".pyo"}:
            candidates.append(path)
        if path.parent.name == "__pycache__":
            candidates.append(path.parent)
    for dirpath, dirnames, _filenames in os.walk(ROOT):
        dirnames[:] = sorted(name for name in dirnames if name not in TRANSIENT_SKIP_DIRS)
        current = Path(dirpath)
        if current.name == "__pycache__":
            candidates.append(current)
    # Locally-ignored caches (e.g. __pycache__ from a pytest run) can never be
    # committed, so only caches git would actually pick up fail the gate.
    ignored = git_ignored_paths(candidates)
    problems = sorted({
        relative(path) for path in candidates if relative(path) not in ignored
    })
    if problems:
        raise CheckFailed("Python transient cache scan", problems)
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


def shipped_focus_definition_count(root: Path = ROOT) -> int:
    focus_dir = root / FOCUS_DEFINITION_RELATIVE_DIR
    if not focus_dir.is_dir():
        raise CheckFailed(
            "README Focus count",
            [f"{relative(focus_dir, root)}: missing FocusDefinition directory"],
        )
    return sum(1 for path in focus_dir.iterdir() if path.suffix == ".json")


def readme_focus_count_problems(root: Path = ROOT) -> list[str]:
    readme = root / "README.md"
    if not readme.is_file():
        return ["README.md: missing release overview"]

    text = readme.read_text(encoding="utf-8")
    match = README_FOCI_PATTERN.search(text)
    if match is None:
        return ["README.md: missing advertised Focus count"]

    advertised_count = int(match.group("count"))
    shipped_count = shipped_focus_definition_count(root)
    if advertised_count != shipped_count:
        return [
            "README.md: advertises "
            f"{advertised_count} Foci but ships {shipped_count} FocusDefinition files"
        ]
    return []


def check_readme_focus_count() -> str:
    problems = readme_focus_count_problems()
    if problems:
        raise CheckFailed("README Focus count", problems)
    return f"README Focus count: {shipped_focus_definition_count()} Foci"


def gradle_properties(root: Path = ROOT) -> dict[str, str]:
    properties_path = root / GRADLE_PROPERTIES_RELATIVE_FILE
    properties: dict[str, str] = {}
    for line in properties_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def current_changelog_section(changelog: str, version: str) -> str:
    headings = list(CHANGELOG_HEADING_PATTERN.finditer(changelog))
    for index, match in enumerate(headings):
        if match.group("version") != version:
            continue
        end = headings[index + 1].start() if index + 1 < len(headings) else len(changelog)
        section = changelog[match.start():end].strip()
        body = section.splitlines()[1:]
        if not any(line.strip() for line in body):
            raise ValueError(f"CHANGELOG.md: empty Attuned {version} changelog section")
        return section
    raise ValueError(f"CHANGELOG.md: missing Attuned {version} changelog section")


def modrinth_changelog_problems(root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    try:
        version = gradle_properties(root).get("mod_version")
    except OSError as exc:
        return [f"gradle.properties: {exc}"]
    if not version:
        return ["gradle.properties: missing mod_version"]

    try:
        section = current_changelog_section((root / CHANGELOG_RELATIVE_FILE).read_text(encoding="utf-8"), version)
    except (OSError, ValueError) as exc:
        problems.append(str(exc))
        section = ""
    if section and any(match.group("version") != version for match in CHANGELOG_HEADING_PATTERN.finditer(section)):
        problems.append(f"CHANGELOG.md: Attuned {version} section includes another release heading")

    try:
        build_gradle = (root / BUILD_GRADLE_RELATIVE_FILE).read_text(encoding="utf-8")
    except OSError as exc:
        problems.append(f"build.gradle: {exc}")
        return problems
    if re.search(r"^\s*changelog\s*=.*fileContents\(.*CHANGELOG\.md.*asText\.get\(\)", build_gradle, re.MULTILINE):
        problems.append(
            f"build.gradle: Modrinth changelog must use the current Attuned {version} changelog section"
        )
    elif not MODRINTH_CHANGELOG_PROVIDER_PATTERN.search(build_gradle):
        problems.append(
            f"build.gradle: Modrinth changelog must be provided by currentChangelogSection for Attuned {version}"
        )
    return problems


def check_modrinth_changelog() -> str:
    problems = modrinth_changelog_problems()
    if problems:
        raise CheckFailed("Modrinth changelog", problems)
    version = gradle_properties().get("mod_version", "unknown")
    return f"Modrinth changelog: Attuned {version} section"


def run_checks() -> int:
    checks = (
        check_src_json,
        check_png_resources,
        check_modrinth_gallery_pngs,
        check_readme_focus_count,
        check_modrinth_changelog,
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
