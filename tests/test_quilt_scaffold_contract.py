import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
	return (ROOT / path).read_text(encoding="utf-8")

def test_quilt_gradle_plugin_and_dependencies_are_native() -> None:
	build = read("build.gradle")
	props = read("gradle.properties")
	settings = read("settings.gradle")
	assert "id 'org.quiltmc.loom' version \"${quilt_loom_version}\"" in build
	assert "org.quiltmc:quilt-loader:${project.quilt_loader_version}" in build

	assert "modImplementation \"net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}\"" in build
	assert "org.quiltmc.quilted-fabric-api" not in build
	assert "loader.addMods" not in build
	assert "quilt_loader_version=0.30.0-beta.8" in props
	assert "quilt_loom_version=1.15.1" in props
	assert "fabric_api_version=0.141.4+1.21.11" in props
	assert "net.fabricmc:fabric-loader" not in build
	assert "https://maven.quiltmc.org/repository/release" in settings

def test_quilt_metadata_replaces_fabric_metadata() -> None:
	assert not (ROOT / "src/main/resources/fabric.mod.json").exists()
	mod_file = ROOT / "src/main/resources/quilt.mod.json"
	assert mod_file.exists()
	metadata = json.loads(mod_file.read_text(encoding="utf-8"))
	loader = metadata["quilt_loader"]
	assert loader["id"] == "attuned"
	assert loader["version"] == "${version}"
	assert loader["entrypoints"]["main"] == "dev.attuned.quilt.AttunedQuilt"
	assert loader["entrypoints"]["client"] == "dev.attuned.quilt.AttunedQuiltClient"
	assert metadata["mixin"][0] == "attuned.mixins.json"
	assert metadata["mixin"][1] == {
		"config": "attuned.client.mixins.json",
		"environment": "client",
	}
	if not (ROOT / "src/main/resources/attuned.accesswidener").exists():
		assert "access_widener" not in loader
	dependencies = {entry["id"]: entry["versions"] for entry in loader["depends"]}

	assert dependencies["quilt_loader"] == ">=0.30.0-beta.8"
	assert dependencies["fabric-api"] == ">=0.141.4"
	assert dependencies["minecraft"] == "~1.21.11"
	assert dependencies["java"] == ">=21"
	assert "quilted_fabric_api" not in dependencies
	assert "fabricloader" not in dependencies

def test_quilt_entrypoint_adapters_delegate_to_attuned_initializers() -> None:
	common = read("src/main/java/dev/attuned/quilt/AttunedQuilt.java")
	client = read("src/client/java/dev/attuned/quilt/AttunedQuiltClient.java")
	assert "implements ModInitializer" in common
	assert "new Attuned().onInitialize();" in common
	assert "implements ClientModInitializer" in client
	assert "new AttunedClient().onInitializeClient();" in client

def test_publishing_tags_quilt() -> None:
	build = read("build.gradle")
	assert 'loaders = ["quilt"]' in build
	assert 'loaders = ["fabric"]' not in build

	assert 'required.project "fabric-api"' in build
	assert 'required.project "qsl"' not in build

def test_resonance_hud_readout_helpers_exist() -> None:
	hud = read("src/client/java/dev/attuned/client/hud/CombatHud.java")
	readout = read("src/client/java/dev/attuned/client/AttunementReadout.java")
	assert "AttunementReadout.displayResonance(player)" in hud
	assert "AttunementReadout.Snapshot readout = AttunementReadout.cached(player);" in hud
	assert "private static Snapshot cachedSnapshot;" in readout
	assert "cachedSnapshot = snapshot(player);" in readout
