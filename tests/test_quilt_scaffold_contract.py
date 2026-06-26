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
	assert "modCompileOnly \"net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}\"" in build
	assert "add(\"fabricApiRuntime\", \"net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}\")" in build
	assert "loader.addMods" in build
	assert "org.quiltmc.quilted-fabric-api" not in build
	assert "maven.modrinth:qsl" not in build
	assert 'accessWidenerPath = file("src/main/resources/attuned.accesswidener")' in build
	assert "net.fabricmc:fabric-loader" not in build
	assert "quilt_loader_version=0.17.8" in props
	assert "quilt_loom_version=1.15.1" in props
	assert "fabric_api_version=0.77.0+1.19.2" in props
	assert "https://maven.quiltmc.org/repository/release" in settings
	assert '["assets", "data"].each' in build
	assert "void mirrorResourceDirectories(File outputDir)" in build
	assert 'file("src/main/resources")' in build
	assert "tasks.withType(ProcessResources).configureEach" in build
	assert "mirrorResourceDirectories(destinationDirectory.get().asFile)" in build
	assert "mirrorResourceDirectories(destinationDir)" in build


def test_quilt_metadata_replaces_fabric_metadata() -> None:
	assert not (ROOT / "src/main/resources/fabric.mod.json").exists()
	mod_file = ROOT / "src/main/resources/quilt.mod.json"
	assert mod_file.exists()

	metadata = json.loads(mod_file.read_text(encoding="utf-8"))
	loader = metadata["quilt_loader"]

	assert loader["id"] == "attuned"
	assert loader["entrypoints"]["main"] == "dev.attuned.quilt.AttunedQuilt"
	assert loader["entrypoints"]["client"] == "dev.attuned.quilt.AttunedQuiltClient"
	assert loader["access_widener"] == "attuned.accesswidener"
	assert metadata["mixin"][0] == "attuned.mixins.json"
	assert metadata["mixin"][1] == "attuned.client.mixins.json"

	dependencies = {entry["id"]: entry["versions"] for entry in loader["depends"]}
	assert dependencies["quilt_loader"] == ">=0.17.8"
	assert dependencies["fabric-api"] == ">=0.77.0"
	assert dependencies["minecraft"] == "~1.19.2"
	assert dependencies["java"] == ">=17"
	assert "suggests" not in loader


def test_quilt_entrypoint_adapters_delegate_to_attuned_initializers() -> None:
	common = read("src/main/java/dev/attuned/quilt/AttunedQuilt.java")
	client = read("src/client/java/dev/attuned/quilt/AttunedQuiltClient.java")

	assert "implements ModInitializer" in common
	assert "new Attuned().onInitialize();" in common
	assert "implements ClientModInitializer" in client
	assert "new AttunedClient().onInitializeClient();" in client


def test_publishing_tags_quilt_and_fabric_api() -> None:
	build = read("build.gradle")

	assert 'loaders = ["quilt"]' in build
	assert 'required.project "fabric-api"' in build
	assert 'loaders = ["fabric"]' not in build
	assert 'required.project "qsl"' not in build


def test_resonance_hud_sync_invalidates_readout_cache() -> None:
	client = read("src/client/java/dev/attuned/client/AttunedStateClientSync.java")
	hud = read("src/client/java/dev/attuned/client/hud/CombatHud.java")
	readout = read("src/client/java/dev/attuned/client/AttunementReadout.java")

	assert "ClientPlayNetworking.registerGlobalReceiver(AttunedStatePayload.TYPE" in client
	assert "AttunedAttachments.applySync(local, payload.tag());" in client
	assert "AttunementReadout.invalidate(local);" in client
	assert "AttunementReadout.displayResonance(player)" in hud
	assert "AttunementReadout.Snapshot readout = AttunementReadout.cached(player);" in hud
	assert "public static void invalidate(Player player)" in readout
	assert "cachedSnapshot = snapshot(player);" in readout
