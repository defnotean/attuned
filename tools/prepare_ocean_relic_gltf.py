#!/usr/bin/env python3
"""Prepare the Ocean Relic Trident GLB for Attuned's in-game renderer.

The Meshy source GLB embeds very large texture images. Attuned renders the mesh
with its game-scale item texture, so this script keeps only the mesh buffers and
normalizes the coordinate system into the same model space used by the
Blockbench source file.
"""

from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path


GLB_MAGIC = 0x46546C67
JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942
FLOAT = 5126
UNSIGNED_SHORT = 5123


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument(
		"source",
		nargs="?",
		default=r"C:\Users\ianga\Downloads\Meshy_AI_Ocean_Relic_Trident_0616193855_image-to-3d-texture.glb",
		help="Source GLB exported from Meshy/Blockbench.",
	)
	parser.add_argument(
		"output",
		nargs="?",
		default="src/main/resources/assets/attuned/gltf/ocean_relic_trident.glb",
		help="Normalized output GLB inside the Attuned resource tree.",
	)
	args = parser.parse_args()

	source = Path(args.source)
	output = Path(args.output)
	root, bin_chunk = read_glb(source)
	mesh = root["meshes"][0]["primitives"][0]
	positions = read_float_vectors(root, bin_chunk, mesh["attributes"]["POSITION"], 3)
	normals = read_float_vectors(root, bin_chunk, mesh["attributes"]["NORMAL"], 3)
	uvs = read_float_vectors(root, bin_chunk, mesh["attributes"]["TEXCOORD_0"], 2)
	indices = read_indices(root, bin_chunk, mesh["indices"])

	normalized_positions = [
		(vertex[2] + 0.0032424, -vertex[0] + 0.92233, vertex[1] + 0.0205741)
		for vertex in positions
	]
	normalized_normals = [
		normalize((normal[2], -normal[0], normal[1]))
		for normal in normals
	]

	buffer_bytes = bytearray()
	position_offset = append_float_vectors(buffer_bytes, normalized_positions)
	normal_offset = append_float_vectors(buffer_bytes, normalized_normals)
	uv_offset = append_float_vectors(buffer_bytes, uvs)
	index_offset = append_unsigned_shorts(buffer_bytes, indices)

	gltf = {
		"asset": {
			"version": "2.0",
			"generator": "Attuned tools/prepare_ocean_relic_gltf.py; inspired by ModularMods/MCglTF",
		},
		"scene": 0,
		"scenes": [{"nodes": [0]}],
		"nodes": [{"mesh": 0, "name": "Ocean Relic Trident"}],
		"meshes": [
			{
				"name": "Ocean Relic Trident",
				"primitives": [
					{
						"attributes": {
							"POSITION": 0,
							"NORMAL": 1,
							"TEXCOORD_0": 2,
						},
						"indices": 3,
						"mode": 4,
						"material": 0,
					}
				],
			}
		],
		"materials": [
			{
				"name": "Ocean Relic Trident material",
				"pbrMetallicRoughness": {
					"baseColorFactor": [1.0, 1.0, 1.0, 1.0],
					"metallicFactor": 0.0,
					"roughnessFactor": 0.5,
				},
			}
		],
		"buffers": [{"byteLength": len(buffer_bytes)}],
		"bufferViews": [
			{"buffer": 0, "byteOffset": position_offset, "byteLength": len(normalized_positions) * 12, "target": 34962},
			{"buffer": 0, "byteOffset": normal_offset, "byteLength": len(normalized_normals) * 12, "target": 34962},
			{"buffer": 0, "byteOffset": uv_offset, "byteLength": len(uvs) * 8, "target": 34962},
			{"buffer": 0, "byteOffset": index_offset, "byteLength": len(indices) * 2, "target": 34963},
		],
		"accessors": [
			accessor(0, FLOAT, len(normalized_positions), "VEC3", min_vector(normalized_positions), max_vector(normalized_positions)),
			accessor(1, FLOAT, len(normalized_normals), "VEC3"),
			accessor(2, FLOAT, len(uvs), "VEC2", min_vector(uvs), max_vector(uvs)),
			accessor(3, UNSIGNED_SHORT, len(indices), "SCALAR"),
		],
	}

	output.parent.mkdir(parents=True, exist_ok=True)
	write_glb(output, gltf, bytes(buffer_bytes))
	print(f"Wrote {output} ({output.stat().st_size:,} bytes)")


def read_glb(path: Path) -> tuple[dict, bytes]:
	data = path.read_bytes()
	magic, version, declared_length = struct.unpack_from("<III", data, 0)
	if magic != GLB_MAGIC or version != 2 or declared_length != len(data):
		raise ValueError(f"{path} is not a valid GLB v2 file")

	json_chunk = None
	bin_chunk = None
	offset = 12
	while offset < len(data):
		chunk_length, chunk_type = struct.unpack_from("<II", data, offset)
		offset += 8
		chunk = data[offset:offset + chunk_length]
		offset += chunk_length
		if chunk_type == JSON_CHUNK:
			json_chunk = chunk
		elif chunk_type == BIN_CHUNK:
			bin_chunk = chunk

	if json_chunk is None or bin_chunk is None:
		raise ValueError(f"{path} must contain JSON and BIN chunks")
	return json.loads(json_chunk.decode("utf-8").rstrip(" \0")), bin_chunk


def read_float_vectors(root: dict, bin_chunk: bytes, accessor_index: int, width: int) -> list[tuple[float, ...]]:
	accessor_data = root["accessors"][accessor_index]
	if accessor_data["componentType"] != FLOAT:
		raise ValueError("Only float vector accessors are supported")
	view = root["bufferViews"][accessor_data["bufferView"]]
	offset = view.get("byteOffset", 0) + accessor_data.get("byteOffset", 0)
	stride = view.get("byteStride", width * 4)
	values = []
	for index in range(accessor_data["count"]):
		values.append(struct.unpack_from("<" + "f" * width, bin_chunk, offset + index * stride))
	return values


def read_indices(root: dict, bin_chunk: bytes, accessor_index: int) -> list[int]:
	accessor_data = root["accessors"][accessor_index]
	if accessor_data["componentType"] != UNSIGNED_SHORT:
		raise ValueError("Ocean Relic source should use unsigned-short indices")
	view = root["bufferViews"][accessor_data["bufferView"]]
	offset = view.get("byteOffset", 0) + accessor_data.get("byteOffset", 0)
	stride = view.get("byteStride", 2)
	return [
		struct.unpack_from("<H", bin_chunk, offset + index * stride)[0]
		for index in range(accessor_data["count"])
	]


def append_float_vectors(buffer_bytes: bytearray, values: list[tuple[float, ...]]) -> int:
	offset = align(buffer_bytes, 4)
	for value in values:
		buffer_bytes.extend(struct.pack("<" + "f" * len(value), *value))
	return offset


def append_unsigned_shorts(buffer_bytes: bytearray, values: list[int]) -> int:
	offset = align(buffer_bytes, 2)
	for value in values:
		buffer_bytes.extend(struct.pack("<H", value))
	return offset


def align(buffer_bytes: bytearray, alignment: int) -> int:
	while len(buffer_bytes) % alignment:
		buffer_bytes.append(0)
	return len(buffer_bytes)


def normalize(value: tuple[float, float, float]) -> tuple[float, float, float]:
	length = sum(component * component for component in value) ** 0.5
	if length <= 1.0e-8:
		return (0.0, 1.0, 0.0)
	return tuple(component / length for component in value)


def min_vector(values: list[tuple[float, ...]]) -> list[float]:
	return [min(value[index] for value in values) for index in range(len(values[0]))]


def max_vector(values: list[tuple[float, ...]]) -> list[float]:
	return [max(value[index] for value in values) for index in range(len(values[0]))]


def accessor(buffer_view: int, component_type: int, count: int, kind: str,
		minimum: list[float] | None = None, maximum: list[float] | None = None) -> dict:
	result = {
		"bufferView": buffer_view,
		"byteOffset": 0,
		"componentType": component_type,
		"count": count,
		"type": kind,
	}
	if minimum is not None:
		result["min"] = minimum
	if maximum is not None:
		result["max"] = maximum
	return result


def write_glb(path: Path, root: dict, bin_chunk: bytes) -> None:
	json_bytes = json.dumps(root, separators=(",", ":")).encode("utf-8")
	json_bytes += b" " * ((4 - len(json_bytes) % 4) % 4)
	padded_bin = bin_chunk + b"\0" * ((4 - len(bin_chunk) % 4) % 4)
	length = 12 + 8 + len(json_bytes) + 8 + len(padded_bin)
	with path.open("wb") as file:
		file.write(struct.pack("<III", GLB_MAGIC, 2, length))
		file.write(struct.pack("<II", len(json_bytes), JSON_CHUNK))
		file.write(json_bytes)
		file.write(struct.pack("<II", len(padded_bin), BIN_CHUNK))
		file.write(padded_bin)


if __name__ == "__main__":
	main()
