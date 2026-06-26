from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_neoforge_build_uses_moddev_and_neoforge_coordinate():
    build = read("build.gradle")
    props = read("gradle.properties")

    assert "id 'net.neoforged.moddev' version" in build
    assert "neoForge {" in build
    assert "version = project.neo_version" in build
    assert "minecraft_version=1.20.6" in props
    assert "neo_version=20.6.139" in props
    assert "neoforge_version_range=[20.6,)" in props
    assert "fabric_api_version" not in props


def test_neoforge_metadata_replaces_fabric_metadata():
    metadata_path = ROOT / "src/main/resources/META-INF/neoforge.mods.toml"
    assert metadata_path.exists()

    metadata = metadata_path.read_text(encoding="utf-8")
    assert 'modLoader="javafml"' in metadata
    assert 'modId="attuned"' in metadata
    assert 'config="attuned.mixins.json"' in metadata
    assert 'config="attuned.client.mixins.json"' in metadata
    assert 'modId="neoforge"' in metadata
    assert 'modId="minecraft"' in metadata
    assert "[features.attuned]" in metadata
    assert "[[features.attuned]]" not in metadata
    assert not (ROOT / "src/main/resources/fabric.mod.json").exists()


def test_neoforge_publish_and_docs_are_loader_scoped():
    build = read("build.gradle")
    checklist = read("docs/loader-porting/neoforge-1.20.6-checklist.md")

    assert 'loaders = ["neoforge"]' in build
    assert "fabric-api" not in build
    assert "Functionality audit" in checklist
    assert "player attunement state persistence/sync" in checklist
    assert "Focus ability networking" in checklist
    assert "server/client init events" in checklist


def test_neoforge_entrypoints_do_not_use_fabric_initializers():
    common = read("src/main/java/dev/attuned/Attuned.java")
    client = read("src/client/java/dev/attuned/client/AttunedClient.java")

    assert "import net.neoforged.fml.common.Mod;" in common
    assert "@Mod(Attuned.MOD_ID)" in common
    assert "public Attuned(IEventBus modEventBus)" in common
    assert "NeoForgeEventBuses.setModEventBus(modEventBus)" in common
    assert "initClientWhenPresent()" in common
    assert "ModInitializer" not in common
    assert "ClientModInitializer" not in client
    assert "public static void init()" in client


def test_neoforge_runtime_registrations_use_deferred_registers():
    bridge = read("src/main/java/dev/attuned/platform/NeoForgeDeferredRegistries.java")
    common = read("src/main/java/dev/attuned/Attuned.java")

    assert "DeferredRegister.createDataComponents" in bridge
    assert "DeferredRegister.createItems" in bridge
    assert "DeferredRegister.createBlocks" in bridge
    assert "NeoForgeDeferredRegistries.register(modEventBus)" in common

    for source_path in (
        "src/main/java/dev/attuned/content/AttunedComponents.java",
        "src/main/java/dev/attuned/content/AttunedContent.java",
        "src/main/java/dev/attuned/content/AttunedCreativeTabs.java",
        "src/main/java/dev/attuned/menu/AltarMenuType.java",
        "src/main/java/dev/attuned/menu/SatchelMenuType.java",
        "src/main/java/dev/attuned/menu/ReweavingMenuType.java",
    ):
        assert "Registry.register(" not in read(source_path)


def test_neoforge_owner_state_sync_invalidates_hud_cache():
    attachments = read("src/main/java/dev/attuned/attunement/AttunedAttachments.java")
    networking = read("src/main/java/dev/attuned/network/JournalNetworking.java")
    client = read("src/client/java/dev/attuned/client/AttunementStateClient.java")
    client_init = read("src/client/java/dev/attuned/client/AttunedClient.java")

    assert "new AttunementStatePayload(" in attachments
    assert "PayloadTypeRegistry.playS2C().register(AttunementStatePayload.TYPE, AttunementStatePayload.CODEC);" in networking
    assert "AttunedAttachments.applySyncedState(local, payload);" in client
    assert "AttunementReadout.invalidate(local);" in client
    assert "AttunementStateClient.init();" in client_init
