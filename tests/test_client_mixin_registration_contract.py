from __future__ import annotations

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT_MIXINS = ROOT / "src" / "client" / "resources" / "attuned.client.mixins.json"


class ClientMixinRegistrationContractTest(unittest.TestCase):
    def test_registered_client_mixins_have_source_files(self) -> None:
        config = json.loads(CLIENT_MIXINS.read_text(encoding="utf-8"))
        package = config["package"]
        source_dir = ROOT / "src" / "client" / "java" / Path(package.replace(".", "/"))

        for mixin in config.get("client", []):
            source = source_dir / f"{mixin}.java"
            self.assertTrue(source.is_file(), f"Registered client mixin has no source file: {mixin}")


if __name__ == "__main__":
    unittest.main()