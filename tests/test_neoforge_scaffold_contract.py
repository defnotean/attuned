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
    assert "minecraft_version=1.21.11" in props
    assert "neo_version=" in props
    assert "neoforge_version_range=" in props
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
    assert 'modId="lootr"' in metadata
    assert "[features.attuned]" in metadata
    assert "[[features.attuned]]" not in metadata
    assert not (ROOT / "src/main/resources/fabric.mod.json").exists()


def test_neoforge_publish_metadata_is_loader_scoped():
    build = read("build.gradle")

    assert 'loaders = ["neoforge"]' in build
    assert "fabric-api" not in build


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


def test_neoforge_payload_registry_builds_channel_and_handlers():
    payload_registry = read("src/main/java/net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.java")
    server_networking = read("src/main/java/net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.java")
    client_networking = read("src/client/java/net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.java")
    common = read("src/main/java/dev/attuned/Attuned.java")

    assert "RegisterPayloadHandlersEvent" in payload_registry
    assert "PayloadRegistrar" in payload_registry
    assert "PacketDistributor.sendToPlayer" in server_networking
    assert (
        "ClientPacketDistributor.sendToServer" in client_networking
        or "PacketDistributor.sendToServer" in client_networking
    )
    assert "PayloadTypeRegistry.buildForgeChannel();" in common


def test_neoforge_owner_state_sync_invalidates_hud_cache():
    attachments = read("src/main/java/dev/attuned/attunement/AttunedAttachments.java")
    networking = read("src/main/java/dev/attuned/network/JournalNetworking.java")
    client = read("src/client/java/dev/attuned/client/AttunementStateClient.java")
    client_init = read("src/client/java/dev/attuned/client/AttunedClient.java")

    assert "new AttunementStatePayload(" in attachments
    assert "ServerPlayNetworking.send(serverPlayer, new AttunementStatePayload(" in attachments
    assert "PayloadTypeRegistry.playS2C().register(AttunementStatePayload.TYPE, AttunementStatePayload.CODEC);" in networking
    assert "ClientPlayNetworking.registerGlobalReceiver(AttunementStatePayload.TYPE" in client
    assert "AttunedAttachments.applySyncedState(local, payload);" in client
    assert "AttunementReadout.invalidate(local);" in client
    assert "AttunementStateClient.init();" in client_init
