from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = ROOT / "gradle.properties"
VERIFICATION_METADATA = ROOT / "gradle" / "verification-metadata.xml"
XML_NAMESPACE = "https://schema.gradle.org/dependency-verification"


def gradle_properties() -> dict[str, str]:
    properties: dict[str, str] = {}
    for line in PROPERTIES.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key, value = stripped.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


class ForgeDependencyVerificationContractTest(unittest.TestCase):
    def test_locally_generated_mavenizer_artifacts_are_trusted_by_exact_coordinate(self) -> None:
        properties = gradle_properties()
        if "forge_version" not in properties:
            self.skipTest("Forge-only dependency verification contract")

        minecraft_version = properties["minecraft_version"]
        forge_version = properties["forge_version"]
        forge_component_version = f"{minecraft_version}-{forge_version}"
        root = ET.parse(VERIFICATION_METADATA).getroot()
        trusts = root.findall(f"./{{{XML_NAMESPACE}}}configuration/"
                              f"{{{XML_NAMESPACE}}}trusted-artifacts/"
                              f"{{{XML_NAMESPACE}}}trust")

        client_extra_trusts = [
            trust for trust in trusts
            if trust.get("group") == "net.minecraft"
            and trust.get("name") == "client-extra"
            and trust.get("version", "").startswith(f"{minecraft_version}-")
        ]
        self.assertEqual(1, len(client_extra_trusts))
        self.assertEqual(
            f"client-extra-{client_extra_trusts[0].get('version')}.jar",
            client_extra_trusts[0].get("file"),
        )

        expected_forge_files = {
            f"forge-{forge_component_version}.jar",
            f"forge-{forge_component_version}.module",
            f"forge-{forge_component_version}-metadata.zip",
        }
        actual_forge_files = {
            trust.get("file")
            for trust in trusts
            if trust.get("group") == "net.minecraftforge"
            and trust.get("name") == "forge"
            and trust.get("version") == forge_component_version
        }
        self.assertEqual(expected_forge_files, actual_forge_files)


if __name__ == "__main__":
    unittest.main()
