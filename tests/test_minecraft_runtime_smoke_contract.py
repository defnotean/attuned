from pathlib import Path
import importlib.util
from unittest import mock
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SMOKE_SCRIPT = ROOT / "tools" / "minecraft_runtime_smoke.py"

spec = importlib.util.spec_from_file_location("minecraft_runtime_smoke", SMOKE_SCRIPT)
smoke = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(smoke)


class MinecraftRuntimeSmokeContractTest(unittest.TestCase):
	def test_success_signal_accepts_plain_and_ansi_server_ready_lines(self) -> None:
		plain = "[Server thread/INFO]: Done (12.345s)! For help, type \"help\""
		ansi = "\x1b[32m[Server thread/INFO]: Done (1.0s)! For help, type 'help'\x1b[0m"

		self.assertTrue(smoke.is_success_line(plain))
		self.assertTrue(smoke.is_success_line(ansi))

	def test_log_scan_flags_runtime_resource_mixin_and_server_failures(self) -> None:
		log = "\n".join((
			"[main/INFO]: Loading Minecraft",
			"[main/ERROR]: Critical injection failure: Callback method missing in attuned mixin",
			"[Worker/ERROR]: Unable to load model: attuned:item/ocean_relic_trident",
			"[Server thread/ERROR]: Encountered an unexpected exception",
		))

		problems = smoke.scan_log_text(log)

		self.assertEqual(["mixin", "resource/model", "server"], [problem.category for problem in problems])
		self.assertEqual([2, 3, 4], [problem.line_number for problem in problems])

	def test_log_scan_ignores_nonfatal_warnings_and_realms_auth_noise(self) -> None:
		log = "\n".join((
			"[Render thread/WARN]: Missing optional icon metadata for unrelated menu art",
			"[Render thread/WARN]: Realms authentication returned 401",
			"[Server thread/INFO]: Done (4.2s)! For help, type 'help'",
		))

		self.assertEqual([], smoke.scan_log_text(log))

	def test_gradle_command_keeps_gradle_args_before_task_and_server_args_after_task(self) -> None:
		command = smoke.build_gradle_command(
			"./gradlew",
			"runServer",
			"nogui --demo",
			("--offline",),
		)

		self.assertEqual(
			["./gradlew", "--no-daemon", "--console=plain", "--offline", "runServer", "--args=nogui --demo"],
			command,
		)

	def test_server_properties_are_created_once_for_smoke_runs(self) -> None:
		with tempfile.TemporaryDirectory() as tmp:
			root = Path(tmp)
			path, changed = smoke.ensure_server_properties(root)

			self.assertTrue(changed)
			self.assertEqual(root / "run" / "server.properties", path)
			content = path.read_text(encoding="utf-8")
			self.assertIn("server-ip=127.0.0.1", content)
			self.assertIn("server-port=0", content)

			_, second_changed = smoke.ensure_server_properties(root)
			self.assertFalse(second_changed)

	def test_server_properties_rewrite_stale_local_smoke_configuration(self) -> None:
		with tempfile.TemporaryDirectory() as tmp:
			root = Path(tmp)
			properties_path = root / "run" / "server.properties"
			properties_path.parent.mkdir()
			properties_path.write_text("online-mode=false\nserver-port=25565\n", encoding="utf-8")

			_, changed = smoke.ensure_server_properties(root)

			self.assertTrue(changed)
			self.assertEqual(smoke.smoke_server_properties_content(), properties_path.read_text(encoding="utf-8"))

	def test_eula_is_not_accepted_unless_requested(self) -> None:
		args = smoke.parse_args([])

		self.assertFalse(args.accept_eula)

	def test_console_echo_replaces_characters_not_supported_by_stdout_encoding(self) -> None:
		class Cp1252Stdout:
			encoding = "cp1252"

			def __init__(self) -> None:
				self.writes: list[str] = []
				self.flush_count = 0

			def write(self, text: str) -> int:
				text.encode(self.encoding)
				self.writes.append(text)
				return len(text)

			def flush(self) -> None:
				self.flush_count += 1

		stdout = Cp1252Stdout()

		with mock.patch.object(smoke.sys, "stdout", stdout):
			smoke.write_stdout("Completed in 1 \N{GREEK SMALL LETTER MU}s\n")

		self.assertEqual(["Completed in 1 ?s\n"], stdout.writes)
		self.assertEqual(1, stdout.flush_count)


if __name__ == "__main__":
	unittest.main()
