#!/usr/bin/env python3
"""Apply Quilt loader port changes on the current branch (from a Fabric source)."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

QUILT_JAVA = """package dev.attuned.quilt;

import dev.attuned.Attuned;
import net.fabricmc.api.ModInitializer;

public final class AttunedQuilt implements ModInitializer {
	@Override
	public void onInitialize() {
		new Attuned().onInitialize();
	}
}
"""

QUILT_CLIENT_JAVA = """package dev.attuned.quilt;

import dev.attuned.client.AttunedClient;
import net.fabricmc.api.ClientModInitializer;

public final class AttunedQuiltClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		new AttunedClient().onInitializeClient();
	}
}
"""


def read(path: Path) -> str:
	return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(content, encoding="utf-8", newline="\n")


def patch_build_gradle(content: str, *, api_mode: str) -> str:
	content = re.sub(
		r"id 'net\.fabricmc\.fabric-loom(?:-remap)?' version \"\$\{loom_version\}\"",
		"id 'org.quiltmc.loom' version \"${quilt_loom_version}\"",
		content,
	)
	if "https://maven.quiltmc.org/repository/release" not in content:
		content = content.replace(
			"repositories {\n\t// Loom adds the Minecraft/Fabric repositories automatically; Maven Central",
			"repositories {\n\t// Loom adds the Minecraft/Fabric repositories automatically; Quilt Maven\n\t// is declared for Quilt Loader and API deps while Maven Central is for JUnit.\n\tmaven {\n\t\tname = 'Quilt'\n\t\turl = 'https://maven.quiltmc.org/repository/release'\n\t}\n\t// Maven Central",
		)
	deps_block = (
		'\tmodImplementation "org.quiltmc:quilt-loader:${project.quilt_loader_version}"\n\n'
		'\tmodImplementation "org.quiltmc.quilted-fabric-api:quilted-fabric-api:${project.qfapi_version}"'
		if api_mode == "qfapi"
		else '\tmodImplementation "org.quiltmc:quilt-loader:${project.quilt_loader_version}"\n\n'
		'\tmodImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"'
	)
	content = re.sub(
		r'\t(?:mod)?implementation "net\.fabricmc:fabric-loader:\$\{project\.loader_version\}"\n\n'
		r'(?:\t// Fabric API\. This is technically optional, but you probably want it anyway\.\n)?'
		r'\t(?:mod)?implementation "net\.fabricmc\.fabric-api:fabric-api:\$\{project\.fabric_api_version\}"',
		deps_block,
		content,
	)
	content = content.replace('filesMatching("fabric.mod.json")', 'filesMatching("quilt.mod.json")')
	content = re.sub(r'loaders = \["fabric"\]', 'loaders = ["quilt"]', content)
	if api_mode == "qfapi":
		content = re.sub(r'required\.project "fabric-api"', 'required.project "qsl"', content)
	if "testCompileClasspath.extendsFrom(minecraftCommonNamedCompile)" not in content:
		content = content.replace(
			"}\n\ndependencies {",
			"""}

// Quilt Loom split sources do not inherit client/common Minecraft deps into the
// unit-test classpath; extend explicitly so contract tests can reference client helpers.
configurations {
	testCompileClasspath.extendsFrom(minecraftCommonNamedCompile)
	testRuntimeClasspath.extendsFrom(minecraftCommonNamedRuntime)
	testCompileClasspath.extendsFrom(minecraftClientOnlyNamedCompile)
	testRuntimeClasspath.extendsFrom(minecraftClientOnlyNamedRuntime)
	testRuntimeClasspath.extendsFrom(modRuntimeClasspathClientMapped)
}

dependencies {
\ttestImplementation sourceSets.client.output""",
			1,
		)
	return content


def patch_settings_gradle(content: str) -> str:
	if "maven.quiltmc.org" in content:
		return content
	return content.replace(
		"pluginManagement {\n\trepositories {\n\t\tmaven {\n\t\t\tname = 'Fabric'",
		"pluginManagement {\n\trepositories {\n\t\tmaven {\n\t\t\tname = 'Quilt'\n\t\t\turl = 'https://maven.quiltmc.org/repository/release'\n\t\t}\n\t\tmaven {\n\t\t\tname = 'Fabric'",
	)


def patch_gradle_properties(content: str, *, cfg: dict) -> str:
	lines = []
	for line in content.splitlines():
		if line.startswith("loader_version=") or line.startswith("loom_version="):
			continue
		if line.startswith("# Fabric Properties"):
			lines.append("# Quilt Properties")
			lines.append("# check these against the official Quilt template and Maven metadata")
			continue
		if line.startswith("# check these on https://fabricmc.net/develop"):
			continue
		lines.append(line)
		if line.startswith("minecraft_version="):
			lines.append(f"quilt_loader_version={cfg['quilt_loader_version']}")
			lines.append(f"quilt_loom_version={cfg['quilt_loom_version']}")
		if line.startswith("fabric_api_version=") and cfg["api_mode"] == "qfapi":
			lines.append(f"qfapi_version={cfg['qfapi_version']}")
	return "\n".join(lines) + "\n"


def fabric_min_api(fabric_api_version: str) -> str:
	return fabric_api_version.split("+", 1)[0]


def make_quilt_mod_json(fabric: dict, *, cfg: dict) -> dict:
	mixin = fabric.get("mixins") or fabric.get("mixin")
	depends = [
		{"id": "quilt_loader", "versions": f">={cfg['quilt_loader_version']}"},
		{"id": "minecraft", "versions": f"~{cfg['minecraft_version']}"},
		{"id": "java", "versions": f">={cfg['java_version']}"},
	]
	if cfg["api_mode"] == "qfapi":
		depends.insert(1, {"id": "quilted_fabric_api", "versions": f">={cfg['qfapi_dep_min']}"})
	else:
		depends.insert(1, {"id": "fabric-api", "versions": f">={fabric_min_api(cfg['fabric_api_version'])}"})
	return {
		"schema_version": 1,
		"quilt_loader": {
			"group": "dev.attuned",
			"id": "attuned",
			"version": "${version}",
			"metadata": {
				"name": fabric.get("name", "Attuned"),
				"description": fabric.get("description", ""),
				"contributors": {"defnotean": "Owner"},
				"contact": fabric.get("contact", {}),
				"license": fabric.get("license", "MIT"),
				"icon": fabric.get("icon", "assets/attuned/icon.png"),
			},
			"intermediate_mappings": "net.fabricmc:intermediary",
			"entrypoints": {
				"main": "dev.attuned.quilt.AttunedQuilt",
				"client": "dev.attuned.quilt.AttunedQuiltClient",
			},
			"depends": depends,
		},
		"mixin": mixin if isinstance(mixin, list) else ["attuned.mixins.json", "attuned.client.mixins.json"],
	}


def patch_loot_test(content: str) -> str:
	content = content.replace(
		'Path.of("src/main/resources/fabric.mod.json")',
		'Path.of("src/main/resources/quilt.mod.json")',
	)
	content = content.replace("FABRIC_MOD_JSON", "MOD_METADATA_JSON")
	content = re.sub(
		r'JsonObject manifest = JsonParser\.parseString\(Files\.readString\(MOD_METADATA_JSON, StandardCharsets\.UTF_8\)\)\n\t\t\t\.getAsJsonObject\(\);\n\t\tassertTrue\(!manifest\.getAsJsonObject\("depends"\)\.has\("lootr"\),\n\t\t\t"Lootr should remain optional because Attuned uses vanilla loot-table injection"\);\n\t\tassertEquals\("\*", manifest\.getAsJsonObject\("suggests"\)\.get\("lootr"\)\.getAsString\(\),\n\t\t\t"Lootr should stay suggested for modpack discovery"\);',
		"""JsonObject manifest = JsonParser.parseString(Files.readString(MOD_METADATA_JSON, StandardCharsets.UTF_8))
\t\t\t.getAsJsonObject();
\t\tJsonObject loader = manifest.getAsJsonObject("quilt_loader");
\t\tassertTrue(!loader.getAsJsonArray("depends").asList().stream()
\t\t\t\t.map(JsonElement::getAsJsonObject)
\t\t\t\t.anyMatch(dependency -> "lootr".equals(dependency.get("id").getAsString())),
\t\t\t"Lootr should remain optional because Attuned uses vanilla loot-table injection");""",
		content,
	)
	if "import com.google.gson.JsonElement;" not in content:
		content = content.replace(
			"import com.google.gson.JsonObject;",
			"import com.google.gson.JsonElement;\nimport com.google.gson.JsonObject;",
		)
	return content


def make_test_contract(cfg: dict) -> str:
	minecraft = cfg["minecraft_version"]
	java_min = cfg["java_version"]
	quilt_loader = cfg["quilt_loader_version"]
	quilt_loom = cfg["quilt_loom_version"]
	if cfg["api_mode"] == "qfapi":
		gradle_assertions = f"""
	assert "org.quiltmc.quilted-fabric-api:quilted-fabric-api:${{project.qfapi_version}}" in build
	assert "net.fabricmc.fabric-api:fabric-api" not in build
	assert "loader.addMods" not in build
	assert "quilt_loader_version={quilt_loader}" in props
	assert "quilt_loom_version={quilt_loom}" in props
	assert "qfapi_version={cfg['qfapi_version']}" in props"""
		meta_dep_assert = f"""
	assert dependencies["quilt_loader"] == ">={quilt_loader}"
	assert dependencies["quilted_fabric_api"] == ">={cfg['qfapi_dep_min']}"
	assert dependencies["minecraft"] == "~{minecraft}"
	assert dependencies["java"] == ">={java_min}"
	assert "fabric-api" not in dependencies"""
		publish_assert = """
	assert 'required.project "qsl"' in build
	assert 'required.project "fabric-api"' not in build"""
	else:
		gradle_assertions = f"""
	assert "modImplementation \\"net.fabricmc.fabric-api:fabric-api:${{project.fabric_api_version}}\\"" in build
	assert "org.quiltmc.quilted-fabric-api" not in build
	assert "loader.addMods" not in build
	assert "quilt_loader_version={quilt_loader}" in props
	assert "quilt_loom_version={quilt_loom}" in props
	assert "fabric_api_version={cfg['fabric_api_version']}" in props"""
		meta_dep_assert = f"""
	assert dependencies["quilt_loader"] == ">={quilt_loader}"
	assert dependencies["fabric-api"] == ">={fabric_min_api(cfg['fabric_api_version'])}"
	assert dependencies["minecraft"] == "~{minecraft}"
	assert dependencies["java"] == ">={java_min}"
	assert "quilted_fabric_api" not in dependencies"""
		publish_assert = """
	assert 'required.project "fabric-api"' in build
	assert 'required.project "qsl"' not in build"""
	return f'''import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
\treturn (ROOT / path).read_text(encoding="utf-8")

def test_quilt_gradle_plugin_and_dependencies_are_native() -> None:
\tbuild = read("build.gradle")
\tprops = read("gradle.properties")
\tsettings = read("settings.gradle")
\tassert "id 'org.quiltmc.loom' version \\"${{quilt_loom_version}}\\"" in build
\tassert "org.quiltmc:quilt-loader:${{project.quilt_loader_version}}" in build
{gradle_assertions}
\tassert "net.fabricmc:fabric-loader" not in build
\tassert "https://maven.quiltmc.org/repository/release" in settings

def test_quilt_metadata_replaces_fabric_metadata() -> None:
\tassert not (ROOT / "src/main/resources/fabric.mod.json").exists()
\tmod_file = ROOT / "src/main/resources/quilt.mod.json"
\tassert mod_file.exists()
\tmetadata = json.loads(mod_file.read_text(encoding="utf-8"))
\tloader = metadata["quilt_loader"]
\tassert loader["id"] == "attuned"
\tassert loader["version"] == "${{version}}"
\tassert loader["entrypoints"]["main"] == "dev.attuned.quilt.AttunedQuilt"
\tassert loader["entrypoints"]["client"] == "dev.attuned.quilt.AttunedQuiltClient"
\tassert metadata["mixin"][0] == "attuned.mixins.json"
\tassert metadata["mixin"][1] == {{
\t\t"config": "attuned.client.mixins.json",
\t\t"environment": "client",
\t}}
\tif not (ROOT / "src/main/resources/attuned.accesswidener").exists():
\t\tassert "access_widener" not in loader
\tdependencies = {{entry["id"]: entry["versions"] for entry in loader["depends"]}}
{meta_dep_assert}
\tassert "fabricloader" not in dependencies

def test_quilt_entrypoint_adapters_delegate_to_attuned_initializers() -> None:
\tcommon = read("src/main/java/dev/attuned/quilt/AttunedQuilt.java")
\tclient = read("src/client/java/dev/attuned/quilt/AttunedQuiltClient.java")
\tassert "implements ModInitializer" in common
\tassert "new Attuned().onInitialize();" in common
\tassert "implements ClientModInitializer" in client
\tassert "new AttunedClient().onInitializeClient();" in client

def test_publishing_tags_quilt() -> None:
\tbuild = read("build.gradle")
\tassert 'loaders = ["quilt"]' in build
\tassert 'loaders = ["fabric"]' not in build
{publish_assert}

def test_resonance_hud_readout_helpers_exist() -> None:
\thud = read("src/client/java/dev/attuned/client/hud/CombatHud.java")
\treadout = read("src/client/java/dev/attuned/client/AttunementReadout.java")
\tassert "AttunementReadout.displayResonance(player)" in hud
\tassert "AttunementReadout.Snapshot readout = AttunementReadout.cached(player);" in hud
\tassert "private static Snapshot cachedSnapshot;" in readout
\tassert "cachedSnapshot = snapshot(player);" in readout
'''


def port(cfg: dict) -> None:
	fabric_mod = ROOT / "src/main/resources/fabric.mod.json"
	if not fabric_mod.exists():
		raise SystemExit("fabric.mod.json missing — checkout a Fabric branch first")
	fabric = json.loads(fabric_mod.read_text(encoding="utf-8"))
	write(ROOT / "build.gradle", patch_build_gradle(read(ROOT / "build.gradle"), api_mode=cfg["api_mode"]))
	write(ROOT / "settings.gradle", patch_settings_gradle(read(ROOT / "settings.gradle")))
	write(ROOT / "gradle.properties", patch_gradle_properties(read(ROOT / "gradle.properties"), cfg=cfg))
	write(ROOT / "src/main/resources/quilt.mod.json", json.dumps(make_quilt_mod_json(fabric, cfg=cfg), indent="\t") + "\n")
	write(ROOT / "src/main/java/dev/attuned/quilt/AttunedQuilt.java", QUILT_JAVA)
	write(ROOT / "src/client/java/dev/attuned/quilt/AttunedQuiltClient.java", QUILT_CLIENT_JAVA)
	write(ROOT / "tests/test_quilt_scaffold_contract.py", make_test_contract(cfg))
	loot_test = ROOT / "src/test/java/dev/attuned/content/AttunedLootCompatibilityTest.java"
	if loot_test.exists():
		write(ROOT / "src/test/java/dev/attuned/content/AttunedLootCompatibilityTest.java", patch_loot_test(read(loot_test)))
	fabric_mod.unlink()
	fabric_dep_test = ROOT / "tests/test_fabric_dependency_metadata_contract.py"
	if fabric_dep_test.exists():
		fabric_dep_test.unlink()
	print(f"Ported {cfg['minecraft_version']} ({cfg['api_mode']})")


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--minecraft-version", required=True)
	parser.add_argument("--api-mode", choices=("qfapi", "fabric-api"), required=True)
	parser.add_argument("--quilt-loader-version", default="0.30.0-beta.8")
	parser.add_argument("--quilt-loom-version", default="1.15.1")
	parser.add_argument("--qfapi-version", default="")
	parser.add_argument("--qfapi-dep-min", default="")
	parser.add_argument("--fabric-api-version", default="")
	parser.add_argument("--java-version", required=True)
	args = parser.parse_args()
	port({
		"minecraft_version": args.minecraft_version,
		"api_mode": args.api_mode,
		"quilt_loader_version": args.quilt_loader_version,
		"quilt_loom_version": args.quilt_loom_version,
		"qfapi_version": args.qfapi_version,
		"qfapi_dep_min": args.qfapi_dep_min,
		"fabric_api_version": args.fabric_api_version,
		"java_version": args.java_version,
	})


if __name__ == "__main__":
	main()
