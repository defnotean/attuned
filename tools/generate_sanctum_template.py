"""Generate the Attunement Sanctum structure template as deterministic NBT.

The Attunement Sanctum is a single 15x8x15 jigsaw piece placed by the datapack
structure ``attuned:attunement_sanctum``. Rather than capture it with an in-game
structure block, the template is emitted here from a hand-rolled, standard-library
NBT writer -- mirroring the hand-rolled PNG encoder in ``tools/verify_repository.py``.
The output is gzipped, big-endian, and byte-identical across runs (no timestamps,
no randomness), so the generated ``sanctum.nbt`` can be committed and diffed.

Layout (X east, Y up, Z south; local origin at the piece corner):
  * y=0  : a 15x15 floor of polished deepslate bricks.
  * y=1-5: four amethyst-block pillars in from each corner.
  * y=1  : a 3x3 chiseled-deepslate altar dais at the centre.
  * y=2  : a single chest atop the altar, wired to ``attuned:chests/sanctum``.
  * everything else is air.

The ``DataVersion`` is copied from a real vanilla template so the file tracks the
game version instead of pinning a stale constant. Run with no arguments to read
the vanilla igloo template from the Fabric Loom cache and write the template into
the resource tree; pass ``--vanilla`` / ``--out`` to override either path.
"""

from __future__ import annotations

import argparse
import gzip
import json
import struct
import sys
from pathlib import Path

# Block ids used by the sanctum, in a fixed palette order.
AIR = "minecraft:air"
FLOOR = "minecraft:polished_deepslate_bricks"
PILLAR = "minecraft:amethyst_block"
ALTAR = "minecraft:chiseled_deepslate"
CHEST = "minecraft:chest"

SIZE = (15, 8, 15)
CENTER = (7, 7)  # centre column in X/Z for a 0..14 span

ROOT = Path(__file__).resolve().parents[1]
VANILLA_TEMPLATE_ENTRY = "data/minecraft/structure/igloo/top.nbt"
DEFAULT_OUT = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "data"
    / "attuned"
    / "structure"
    / "sanctum.nbt"
)


def active_minecraft_version(root: Path = ROOT) -> str:
    profiles_path = root / "config" / "minecraft-version-profiles.json"
    if profiles_path.is_file():
        data = json.loads(profiles_path.read_text(encoding="utf-8"))
        active = data["active_profile"]
        return data["profiles"][active]["minecraft_version"]

    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("minecraft_version="):
            return line.split("=", 1)[1].strip()
    raise ValueError("could not determine active Minecraft version")


def default_vanilla_candidates(version: str, home: Path = Path.home()) -> list[Path]:
    loom = home / ".gradle" / "caches" / "fabric-loom"
    return [
        loom / "minecraftMaven" / "net" / "minecraft" / "minecraft-common-deobf" / version
            / f"minecraft-common-deobf-{version}.jar",
        loom / "minecraftMaven" / "net" / "minecraft" / "minecraft-merged-deobf" / version
            / f"minecraft-merged-deobf-{version}.jar",
        loom / version / "minecraft-merged.jar",
    ]


def default_vanilla_path(root: Path = ROOT, home: Path = Path.home()) -> Path:
    candidates = default_vanilla_candidates(active_minecraft_version(root), home)
    return next((path for path in candidates if path.is_file()), candidates[0])


# --- Minimal big-endian NBT writer (tag ids used: 1,3,8,9,10). ----------------
def _string(text: str) -> bytes:
    encoded = text.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def _named(tag_id: int, name: str, payload: bytes) -> bytes:
    return struct.pack(">B", tag_id) + _string(name) + payload


def _tag_byte(value: int) -> bytes:
    return struct.pack(">b", value)


def _tag_int(value: int) -> bytes:
    return struct.pack(">i", value)


def _tag_string(value: str) -> bytes:
    return _string(value)


def _tag_int_list(values) -> bytes:
    # TAG_List of TAG_Int (item id 3).
    body = struct.pack(">B", 3) + struct.pack(">i", len(values))
    for value in values:
        body += struct.pack(">i", value)
    return body


def _tag_compound_list(compounds) -> bytes:
    # TAG_List of TAG_Compound (item id 10). Each item is a raw compound body
    # (already terminated with TAG_End).
    body = struct.pack(">B", 10) + struct.pack(">i", len(compounds))
    for compound in compounds:
        body += compound
    return body


def _compound_body(*entries: bytes) -> bytes:
    return b"".join(entries) + struct.pack(">B", 0)  # TAG_End


# --- Vanilla DataVersion lookup. ----------------------------------------------
def _read_data_version(data: bytes) -> int:
    """Pull the ``DataVersion`` int out of an uncompressed NBT byte string.

    The template root is a compound that always carries ``DataVersion`` as a
    top-level TAG_Int; a tiny linear scan keeps this reader independent of the
    full NBT grammar.
    """
    marker = b"\x03" + struct.pack(">H", len("DataVersion")) + b"DataVersion"
    index = data.find(marker)
    if index < 0:
        raise ValueError("vanilla template has no DataVersion tag")
    value_at = index + len(marker)
    return struct.unpack(">i", data[value_at : value_at + 4])[0]


def read_data_version(vanilla_path: Path) -> int:
    """Read ``DataVersion`` from a vanilla template file or merged jar.

    ``vanilla_path`` may be a standalone ``.nbt`` (gzipped or raw) or the
    Minecraft merged jar, in which case the bundled igloo template is used.
    """
    if vanilla_path.suffix == ".jar":
        import zipfile

        with zipfile.ZipFile(vanilla_path) as jar:
            raw = jar.read(VANILLA_TEMPLATE_ENTRY)
    else:
        raw = vanilla_path.read_bytes()
    try:
        data = gzip.decompress(raw)
    except (OSError, EOFError):
        data = raw
    return _read_data_version(data)


# --- Template assembly. -------------------------------------------------------
def _palette() -> list[str]:
    # Stable order -> stable state indices in the blocks list.
    return [AIR, FLOOR, PILLAR, ALTAR, CHEST]


def _palette_compounds() -> list[bytes]:
    palette = _palette()
    compounds = []
    for name in palette:
        if name == CHEST:
            # A chest needs block-state properties; pin them so the relief faces
            # the entrance and the chest is a single, dry container.
            properties = _compound_body(
                _named(8, "facing", _tag_string("south")),
                _named(8, "type", _tag_string("single")),
                _named(8, "waterlogged", _tag_string("false")),
            )
            compounds.append(
                _compound_body(
                    _named(10, "Properties", properties),
                    _named(8, "Name", _tag_string(name)),
                )
            )
        else:
            compounds.append(_compound_body(_named(8, "Name", _tag_string(name))))
    return compounds


def _block(pos, state: int, chest_nbt: bytes | None = None) -> bytes:
    entries = [_named(9, "pos", _tag_int_list(list(pos)))]
    if chest_nbt is not None:
        entries.append(_named(10, "nbt", chest_nbt))
    entries.append(_named(3, "state", _tag_int(state)))
    return _compound_body(*entries)


def _chest_nbt() -> bytes:
    # Block-entity data for the loot chest: id + loot-table reference. The loot
    # table id is stored verbatim and is asserted (uncompressed) by the contract
    # tests on both the Python and Java side.
    return _compound_body(
        _named(8, "LootTable", _tag_string("attuned:chests/sanctum")),
        _named(8, "id", _tag_string("minecraft:chest")),
    )


def _blocks() -> list[bytes]:
    palette = _palette()
    air = palette.index(AIR)
    floor = palette.index(FLOOR)
    pillar = palette.index(PILLAR)
    altar = palette.index(ALTAR)
    chest = palette.index(CHEST)

    width, height, depth = SIZE
    cx, cz = CENTER

    # Pillars sit two blocks in from each corner.
    pillar_xz = {(2, 2), (2, depth - 3), (width - 3, 2), (width - 3, depth - 3)}

    blocks: list[bytes] = []
    for x in range(width):
        for z in range(depth):
            for y in range(height):
                if y == 0:
                    blocks.append(_block((x, y, z), floor))
                    continue
                if (x, z) in pillar_xz and 1 <= y <= 5:
                    blocks.append(_block((x, y, z), pillar))
                    continue
                if y == 1 and cx - 1 <= x <= cx + 1 and cz - 1 <= z <= cz + 1:
                    blocks.append(_block((x, y, z), altar))
                    continue
                if y == 2 and x == cx and z == cz:
                    blocks.append(_block((x, y, z), chest, _chest_nbt()))
                    continue
                blocks.append(_block((x, y, z), air))
    return blocks


def build_template(vanilla_path: Path) -> bytes:
    """Return the gzipped NBT bytes for the sanctum template.

    ``vanilla_path`` supplies the ``DataVersion`` so the template tracks the game
    version. Output is deterministic: the same inputs always yield identical bytes.
    """
    data_version = read_data_version(vanilla_path)

    root = _compound_body(
        _named(9, "size", _tag_int_list(list(SIZE))),
        _named(9, "entities", _tag_compound_list([])),
        _named(9, "blocks", _tag_compound_list(_blocks())),
        _named(9, "palette", _tag_compound_list(_palette_compounds())),
        _named(3, "DataVersion", _tag_int(data_version)),
    )
    # Root is an unnamed compound.
    serialized = struct.pack(">B", 10) + _string("") + root
    # mtime=0 keeps the gzip header byte-identical across runs.
    return gzip.compress(serialized, mtime=0)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--vanilla",
        type=Path,
        default=default_vanilla_path(),
        help="vanilla .nbt template or common/merged jar to read DataVersion from",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=DEFAULT_OUT,
        help="output path for the generated sanctum.nbt",
    )
    args = parser.parse_args(argv)

    template = build_template(args.vanilla)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(template)
    print(f"wrote {args.out} ({len(template)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
