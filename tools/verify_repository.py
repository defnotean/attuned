from __future__ import annotations

import importlib.util
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
ATTUNED_ASSET_RELATIVE_DIR = Path("src/main/resources/assets/attuned")
ATTUNED_LANG_RELATIVE_FILE = Path("src/main/resources/assets/attuned/lang/en_us.json")
MODRINTH_GALLERY_RELATIVE_DIR = Path("docs/modrinth-gallery")
PYTHON_ROOTS = (ROOT / "tools", ROOT / "tests")
SOURCE_SCAN_ROOTS = (ROOT / "src", ROOT / "tools", ROOT / "tests", ROOT / ".github")
JSON_EXTRA_RELATIVE_DIRS = (Path("docs"), Path("config"))
JAVA_SOURCE_RELATIVE_DIRS = (
    Path("src/main/java"),
    Path("src/client/java"),
)
FOCUS_DEFINITION_RELATIVE_DIR = Path("src/main/resources/data/attuned/attuned/focus")
GRADLE_PROPERTIES_RELATIVE_FILE = Path("gradle.properties")
BUILD_GRADLE_RELATIVE_FILE = Path("build.gradle")
CHANGELOG_RELATIVE_FILE = Path("CHANGELOG.md")
VERSION_PROFILE_RELATIVE_FILE = Path("config/minecraft-version-profiles.json")
VERSION_PROFILE_TOOL_RELATIVE_FILE = Path("tools/minecraft_version_profile.py")
CI_WORKFLOW_RELATIVE_FILE = Path(".github/workflows/ci.yml")
CURSEFORGE_TOOL_RELATIVE_FILE = Path("tools/publish_curseforge.py")
VERSIONING_DOC_RELATIVE_FILE = Path("docs/versioning/minecraft-version-migration.md")
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
    ".mcmeta",
    ".md",
    ".properties",
    ".py",
    ".toml",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}
JSON_SOURCE_SUFFIXES = {".json", ".mcmeta"}
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
STATIC_TRANSLATION_PATTERNS = (
    re.compile(r"Component\.translatable\(\"(?P<key>[^\"]+)\"\)"),
    re.compile(r"I18n\.get\(\"(?P<key>[^\"]+)\"\)"),
)
ATTUNED_TRANSLATION_PREFIXES = (
    "advancement.attuned.",
    "block.attuned.",
    "confluence.attuned.",
    "container.attuned.",
    "inspect.attuned.",
    "item.attuned.",
    "journal.attuned.",
    "key.attuned.",
    "message.attuned.",
    "pact.attuned.",
    "screen.attuned.",
    "tooltip.attuned.",
)
PUBLIC_FOCUS_COUNT_RELATIVE_FILES = (
    Path("README.md"),
    Path("docs/platform/modrinth-description.md"),
    Path("docs/platform/curseforge-description.md"),
)
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


def json_source_files(root: Path = ROOT) -> list[Path]:
    scan_roots = list(SOURCE_SCAN_ROOTS) + [root / relative_dir for relative_dir in JSON_EXTRA_RELATIVE_DIRS]
    seen: set[str] = set()
    files: list[Path] = []
    for scan_root in scan_roots:
        for path in iter_files(scan_root, SOURCE_SKIP_DIRS):
            if path.suffix.lower() not in JSON_SOURCE_SUFFIXES:
                continue
            key = str(path.resolve())
            if key in seen:
                continue
            seen.add(key)
            files.append(path)
    return sorted(files)


def check_repository_json() -> str:
    json_files = json_source_files()
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
        raise CheckFailed("repository JSON parsing", problems)
    return f"repository JSON parsing: {len(json_files)} files"


def check_src_json() -> str:
    return check_repository_json()


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
            validate_png_chunks(path)
        except (OSError, ValueError) as exc:
            problems.append(f"{relative(path)}: {exc}")
    for path in sorted(PNG_RESOURCE_ROOT.rglob("*.png.mcmeta")):
        if not path.with_suffix("").is_file():
            problems.append(f"{relative(path)}: missing paired PNG")
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


def iter_json_model_ids(value: object) -> Iterable[str]:
    if isinstance(value, dict):
        model = value.get("model")
        if isinstance(model, str):
            yield model
        for nested in value.values():
            yield from iter_json_model_ids(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from iter_json_model_ids(nested)


def iter_item_definition_model_ids(value: object) -> Iterable[str]:
    yield from iter_json_model_ids(value)


def iter_blockstate_model_ids(value: object) -> Iterable[str]:
    yield from iter_json_model_ids(value)


def iter_model_texture_values(value: object) -> Iterable[str]:
    if isinstance(value, dict):
        textures = value.get("textures")
        if isinstance(textures, dict):
            for texture in textures.values():
                if isinstance(texture, str):
                    yield texture
        for key, nested in value.items():
            if key == "texture" and isinstance(nested, str):
                yield nested
            yield from iter_model_texture_values(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from iter_model_texture_values(nested)


def attuned_texture_path(asset_dir: Path, texture_id: str) -> Path | None:
    if texture_id.startswith("#"):
        return None
    namespace, separator, path = texture_id.partition(":")
    if not separator:
        namespace = "minecraft"
        path = texture_id
    if namespace != "attuned":
        return None
    return asset_dir / "textures" / f"{path}.png"


def attuned_model_path(asset_dir: Path, model_id: str) -> Path | None:
    namespace, separator, path = model_id.partition(":")
    if not separator:
        namespace = "minecraft"
        path = model_id
    if namespace != "attuned":
        return None
    return asset_dir / "models" / f"{path}.json"


def attuned_model_parent_path(asset_dir: Path, parent_id: str) -> Path | None:
    return attuned_model_path(asset_dir, parent_id)


def attuned_asset_reference_problems(root: Path = ROOT) -> list[str]:
    asset_dir = root / ATTUNED_ASSET_RELATIVE_DIR
    problems: list[str] = []
    item_definitions = asset_dir / "items"
    blockstates = asset_dir / "blockstates"
    model_dir = asset_dir / "models"
    if not asset_dir.is_dir():
        return [f"{relative(asset_dir, root)}: missing Attuned asset directory"]

    item_definition_paths = sorted(item_definitions.glob("*.json")) if item_definitions.is_dir() else []
    for path in item_definition_paths:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            problems.append(f"{relative(path, root)}: {exc}")
            continue
        model_ids = sorted(set(iter_item_definition_model_ids(data)))
        if not model_ids:
            problems.append(f"{relative(path, root)}: missing item model id")
            continue
        for model_id in model_ids:
            model_path = attuned_model_path(asset_dir, model_id)
            if model_path is not None and not model_path.is_file():
                problems.append(f"{relative(path, root)}: missing model {model_id}")

    blockstate_paths = sorted(blockstates.glob("*.json")) if blockstates.is_dir() else []
    for path in blockstate_paths:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            problems.append(f"{relative(path, root)}: {exc}")
            continue
        for model_id in sorted(set(iter_blockstate_model_ids(data))):
            model_path = attuned_model_path(asset_dir, model_id)
            if model_path is not None and not model_path.is_file():
                problems.append(f"{relative(path, root)}: missing blockstate model {model_id}")

    model_paths = sorted(model_dir.rglob("*.json")) if model_dir.is_dir() else []
    for path in model_paths:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            problems.append(f"{relative(path, root)}: {exc}")
            continue
        parent_id = data.get("parent")
        if isinstance(parent_id, str):
            parent_path = attuned_model_parent_path(asset_dir, parent_id)
            if parent_path is not None and not parent_path.is_file():
                problems.append(f"{relative(path, root)}: missing parent model {parent_id}")
        for texture_id in sorted(set(iter_model_texture_values(data))):
            texture_path = attuned_texture_path(asset_dir, texture_id)
            if texture_path is not None and not texture_path.is_file():
                problems.append(f"{relative(path, root)}: missing texture {texture_id}")
    return problems


def check_attuned_asset_references() -> str:
    problems = attuned_asset_reference_problems()
    if problems:
        raise CheckFailed("Attuned asset references", problems)
    asset_dir = ROOT / ATTUNED_ASSET_RELATIVE_DIR
    item_defs = len(list((asset_dir / "items").glob("*.json")))
    blockstates = len(list((asset_dir / "blockstates").glob("*.json")))
    models = len(list((asset_dir / "models").rglob("*.json")))
    return f"Attuned asset references: {item_defs} item definitions, {blockstates} blockstates, {models} models"


def is_language_file(path: Path) -> bool:
    return path.suffix.lower() == ".json" and path.parent.name == "lang" and "assets" in path.parts


def language_data_problems(path: Path, data: object, root: Path = ROOT) -> list[str]:
    relative_path = relative(path, root)
    if not isinstance(data, dict):
        return [f"{relative_path}: language file must be a JSON object"]
    problems: list[str] = []
    for key, value in sorted(data.items()):
        if not isinstance(value, str):
            problems.append(f"{relative_path}: {key} must be a string")
    return problems


def language_file_problems(root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    for path in json_source_files(root):
        if not is_language_file(path):
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            problems.append(f"{relative(path, root)}: {exc}")
            continue
        problems.extend(language_data_problems(path, data, root))
    return problems


def check_language_files() -> str:
    problems = language_file_problems()
    if problems:
        raise CheckFailed("Language files", problems)
    count = sum(1 for path in json_source_files() if is_language_file(path))
    return f"Language files: {count} files"


def load_attuned_lang(root: Path = ROOT) -> dict[str, object]:
    lang_path = root / ATTUNED_LANG_RELATIVE_FILE
    with lang_path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    problems = language_data_problems(lang_path, data, root)
    if problems:
        raise ValueError("; ".join(problems))
    return data


def is_attuned_translation_key(key: str) -> bool:
    return key.startswith(ATTUNED_TRANSLATION_PREFIXES)


def static_translation_key_problems(root: Path = ROOT) -> list[str]:
    try:
        lang = load_attuned_lang(root)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        return [str(exc)]

    problems: list[str] = []
    for relative_dir in JAVA_SOURCE_RELATIVE_DIRS:
        source_dir = root / relative_dir
        if not source_dir.is_dir():
            continue
        for path in sorted(source_dir.rglob("*.java")):
            try:
                text = path.read_text(encoding="utf-8")
            except OSError as exc:
                problems.append(f"{relative(path, root)}: {exc}")
                continue
            for pattern in STATIC_TRANSLATION_PATTERNS:
                for match in pattern.finditer(text):
                    key = match.group("key")
                    if is_attuned_translation_key(key) and key not in lang:
                        problems.append(f"{relative(path, root)}: missing lang {key}")
    return sorted(set(problems))


def check_static_translation_keys() -> str:
    problems = static_translation_key_problems()
    if problems:
        raise CheckFailed("Static translation keys", problems)
    return "Static translation keys: Java literals resolve"


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
            "Public Focus counts",
            [f"{relative(focus_dir, root)}: missing FocusDefinition directory"],
        )
    return sum(1 for path in focus_dir.iterdir() if path.suffix == ".json")


def focus_count_doc_problems(path: Path, shipped_count: int, root: Path = ROOT) -> list[str]:
    relative_path = relative(path, root)
    if not path.is_file():
        return [f"{relative_path}: missing release overview"]

    text = path.read_text(encoding="utf-8")
    matches = list(README_FOCI_PATTERN.finditer(text))
    if not matches:
        return [f"{relative_path}: missing advertised Focus count"]

    problems: list[str] = []
    for match in matches:
        advertised_count = int(match.group("count"))
        if advertised_count != shipped_count:
            problems.append(
                f"{relative_path}: advertises "
                f"{advertised_count} Foci but ships {shipped_count} FocusDefinition files"
            )
    return problems


def public_focus_count_problems(root: Path = ROOT) -> list[str]:
    shipped_count = shipped_focus_definition_count(root)
    problems: list[str] = []
    for relative_file in PUBLIC_FOCUS_COUNT_RELATIVE_FILES:
        problems.extend(focus_count_doc_problems(root / relative_file, shipped_count, root))
    return problems


def readme_focus_count_problems(root: Path = ROOT) -> list[str]:
    shipped_count = shipped_focus_definition_count(root)
    return focus_count_doc_problems(root / "README.md", shipped_count, root)


def check_public_focus_counts() -> str:
    problems = public_focus_count_problems()
    if problems:
        raise CheckFailed("Public Focus counts", problems)
    return f"Public Focus counts: {shipped_focus_definition_count()} Foci in {len(PUBLIC_FOCUS_COUNT_RELATIVE_FILES)} docs"


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


def _load_version_profile_tool(root: Path = ROOT):
    tool_path = root / VERSION_PROFILE_TOOL_RELATIVE_FILE
    if not tool_path.is_file():
        raise FileNotFoundError(f"{relative(tool_path, root)} is missing")
    spec = importlib.util.spec_from_file_location("attuned_minecraft_version_profile", tool_path)
    if spec is None or spec.loader is None:
        raise ImportError(f"cannot import {relative(tool_path, root)}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def version_profile_problems(root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    if not (root / VERSION_PROFILE_RELATIVE_FILE).is_file():
        problems.append(f"{VERSION_PROFILE_RELATIVE_FILE}: missing Minecraft version profile registry")
    try:
        profile_tool = _load_version_profile_tool(root)
        problems.extend(profile_tool.validate_repository(root))
        _active_id, active_profile = profile_tool.active_profile(root)
    except (OSError, ImportError, AttributeError, ValueError) as exc:
        problems.append(f"{VERSION_PROFILE_TOOL_RELATIVE_FILE}: {exc}")
        active_profile = {}

    try:
        build_gradle = (root / BUILD_GRADLE_RELATIVE_FILE).read_text(encoding="utf-8")
    except OSError as exc:
        problems.append(f"{BUILD_GRADLE_RELATIVE_FILE}: {exc}")
    else:
        required_build_snippets = {
            "targetJavaVersion = project.java_version.toString().toInteger()": "derive targetJavaVersion from project.java_version",
            "JavaLanguageVersion.of(targetJavaVersion)": "use targetJavaVersion for the Java toolchain",
            "JavaVersion.toVersion(targetJavaVersion)": "use targetJavaVersion for source/target compatibility",
            "gameVersions = [project.minecraft_version.toString()]": "publish Modrinth gameVersions from minecraft_version",
        }
        for snippet, description in required_build_snippets.items():
            if snippet not in build_gradle:
                problems.append(f"{BUILD_GRADLE_RELATIVE_FILE}: must {description}")

    try:
        ci_workflow = (root / CI_WORKFLOW_RELATIVE_FILE).read_text(encoding="utf-8")
    except OSError as exc:
        problems.append(f"{CI_WORKFLOW_RELATIVE_FILE}: {exc}")
    else:
        expected_java = str(active_profile.get("java_version", ""))
        uses_dynamic_profile = (
            "tools/minecraft_version_profile.py current --github-output" in ci_workflow
            and "steps.versions.outputs.java_version" in ci_workflow
        )
        hardcoded_java = re.search(r"java-version:\s*[\"']?(?P<version>\d+)[\"']?", ci_workflow)
        if not uses_dynamic_profile and hardcoded_java is None:
            problems.append(
                f"{CI_WORKFLOW_RELATIVE_FILE}: setup-java must use the active profile Java version"
            )
        if hardcoded_java is not None and expected_java and hardcoded_java.group("version") != expected_java:
            problems.append(
                f"{CI_WORKFLOW_RELATIVE_FILE}: java-version {hardcoded_java.group('version')} "
                f"does not match active profile java_version {expected_java}"
            )

    try:
        curseforge_tool = (root / CURSEFORGE_TOOL_RELATIVE_FILE).read_text(encoding="utf-8")
    except OSError as exc:
        problems.append(f"{CURSEFORGE_TOOL_RELATIVE_FILE}: {exc}")
    else:
        if 'java_version = props["java_version"]' not in curseforge_tool:
            problems.append(f"{CURSEFORGE_TOOL_RELATIVE_FILE}: must read java_version from gradle.properties")

    if not (root / VERSIONING_DOC_RELATIVE_FILE).is_file():
        problems.append(f"{VERSIONING_DOC_RELATIVE_FILE}: missing Minecraft version migration guide")
    return problems


def check_version_profile() -> str:
    problems = version_profile_problems()
    if problems:
        raise CheckFailed("Minecraft version profile", problems)
    active_id, _profile = _load_version_profile_tool().active_profile(ROOT)
    return f"Minecraft version profile: active {active_id}"


def run_checks() -> int:
    checks = (
        check_repository_json,
        check_png_resources,
        check_attuned_asset_references,
        check_language_files,
        check_static_translation_keys,
        check_modrinth_gallery_pngs,
        check_public_focus_counts,
        check_modrinth_changelog,
        check_version_profile,
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
