from __future__ import annotations

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

EXPECTED_DEPENDENCY_RANGES = {
    "1.18.2": {
        "fabricloader": ">=0.18.4",
        "fabric-api": "*",
    },
    "1.19.2": {
        "fabricloader": ">=0.18.4",
        "fabric-api": ">=0.77.0+1.19.2",
    },
    "1.19.4": {
        "fabricloader": ">=0.18.4",
        "fabric-api": "*",
    },
    "1.20.1": {
        "fabricloader": ">=0.18.4",
        "fabric-api": ">=0.92.8+1.20.1",
    },
    "1.20.6": {
        "fabricloader": ">=0.15.10",
        "fabric-api": ">=0.100.0+1.20.6",
    },
    "1.21.1": {
        "fabricloader": ">=0.16.14",
        "fabric-api": ">=0.116.5+1.21.1",
    },
    "1.21.11": {
        "fabricloader": ">=0.18.4",
        "fabric-api": "*",
    },
}


def gradle_properties() -> dict[str, str]:
    properties: dict[str, str] = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


class FabricDependencyMetadataContractTest(unittest.TestCase):
    def test_fabric_mod_json_advertises_verified_runtime_dependency_floors(self) -> None:
        minecraft_version = gradle_properties()["minecraft_version"]
        expected = EXPECTED_DEPENDENCY_RANGES[minecraft_version]
        manifest = json.loads((ROOT / "src" / "main" / "resources" / "fabric.mod.json").read_text(encoding="utf-8"))
        depends = manifest["depends"]

        for dependency, range_spec in expected.items():
            self.assertEqual(
                range_spec,
                depends[dependency],
                f"{minecraft_version} should accept the verified {dependency} runtime floor",
            )


if __name__ == "__main__":
    unittest.main()
