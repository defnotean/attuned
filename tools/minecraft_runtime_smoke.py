from __future__ import annotations

import argparse
import os
import queue
import re
import shlex
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Iterable, NamedTuple, Sequence

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_STARTUP_TIMEOUT_SECONDS = 240.0
DEFAULT_STOP_TIMEOUT_SECONDS = 60.0
ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
SERVER_READY_RE = re.compile(
    r"\bDone \([0-9]+(?:\.[0-9]+)?s\)! For help, type ['\"]help['\"]",
    re.IGNORECASE,
)
_READER_DONE = object()


class LogProblem(NamedTuple):
    line_number: int
    category: str
    line: str
    pattern: str


class SmokeOutcome(NamedTuple):
    return_code: int | None
    saw_start: bool
    timed_out: bool
    forced_shutdown: bool
    stop_sent: bool
    log_lines: list[str]


FATAL_LOG_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    (
        "mixin",
        re.compile(
            r"\b(?:mixin apply|mixin prepare|mixin initiali[sz]ation|mixintransformererror|"
            r"invalidmixinexception|critical injection failure|injection failure|"
            r"mixin config .+ failed|failed .+ mixin|mixin .+ failed)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "resource/model",
        re.compile(
            r"\b(?:unable to load model|failed to load model|error loading model|"
            r"exception loading model|could(?: not|n't) load model)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "resource/model",
        re.compile(
            r"\b(?:could(?: not|n't) parse data file|could(?: not|n't) load data file|"
            r"failed to parse data file|failed to load resource|error loading resource|"
            r"exception loading resource|resource reload failed|failed to load registries|"
            r"failed to load data packs)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "server",
        re.compile(
            r"\b(?:encountered an unexpected exception|exception in server tick loop|"
            r"failed to start the minecraft server|crash report saved|server crashed|"
            r"reported exception thrown|a critical error has occurred|failed to bootstrap|"
            r"bootstrap failed)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "server",
        re.compile(
            r"\b(?:java\.lang\.)?(?:NoClassDefFoundError|ClassNotFoundException|"
            r"ExceptionInInitializerError|LinkageError|VerifyError|AbstractMethodError|"
            r"NoSuchMethodError|NoSuchFieldError)\b"
        ),
    ),
    (
        "server",
        re.compile(
            r"\bjava\.lang\.(?:RuntimeException|IllegalStateException|NullPointerException|Error):"
            r".*\b(?:attuned|minecraft|fabric|server|mixin)\b",
            re.IGNORECASE,
        ),
    ),
    ("server", re.compile(r"\bFATAL\b", re.IGNORECASE)),
)


def strip_ansi(text: str) -> str:
    return ANSI_ESCAPE_RE.sub("", text)


def normalize_log_line(line: str) -> str:
    return strip_ansi(line).rstrip("\r\n")


def is_success_line(line: str) -> bool:
    return bool(SERVER_READY_RE.search(strip_ansi(line)))


def scan_log_lines(lines: Iterable[str], start_line: int = 1) -> list[LogProblem]:
    problems: list[LogProblem] = []
    for offset, raw_line in enumerate(lines):
        line = normalize_log_line(raw_line)
        for category, pattern in FATAL_LOG_PATTERNS:
            if pattern.search(line):
                problems.append(LogProblem(start_line + offset, category, line, pattern.pattern))
                break
    return problems


def scan_log_text(text: str) -> list[LogProblem]:
    return scan_log_lines(text.splitlines())


def write_stdout(text: str) -> None:
    try:
        sys.stdout.write(text)
    except UnicodeEncodeError:
        encoding = sys.stdout.encoding or "utf-8"
        safe_text = text.encode(encoding, errors="replace").decode(encoding, errors="replace")
        sys.stdout.write(safe_text)
    sys.stdout.flush()


def default_gradle_executable() -> str:
    if os.name == "nt":
        return "gradlew.bat"
    return "./gradlew"


def build_gradle_command(
    gradle_executable: str,
    task: str,
    server_args: str,
    extra_gradle_args: Sequence[str],
) -> list[str]:
    command = [gradle_executable, "--no-daemon", "--console=plain", *extra_gradle_args, task]
    if server_args:
        command.append(f"--args={server_args}")
    return command


def format_command(command: Sequence[str]) -> str:
    if os.name == "nt":
        return subprocess.list2cmdline(list(command))
    return shlex.join(command)


def ensure_eula_accepted(project_root: Path) -> tuple[Path, bool]:
    run_dir = project_root / "run"
    run_dir.mkdir(parents=True, exist_ok=True)
    eula_path = run_dir / "eula.txt"
    if eula_path.exists():
        try:
            for line in eula_path.read_text(encoding="utf-8").splitlines():
                if re.fullmatch(r"\s*eula\s*=\s*true\s*", line, re.IGNORECASE):
                    return eula_path, False
        except OSError:
            pass
    eula_path.write_text(
        "# Required for automated dedicated-server smoke runs.\n"
        "# See https://aka.ms/MinecraftEULA before running a local server.\n"
        "eula=true\n",
        encoding="utf-8",
    )
    return eula_path, True


def smoke_server_properties_content() -> str:
    return (
        "# Minimal dedicated-server settings for automated smoke runs.\n"
        "enable-query=false\n"
        "enable-rcon=false\n"
        "online-mode=true\n"
        "server-ip=127.0.0.1\n"
        "server-port=0\n"
        "motd=Attuned runtime smoke check\n"
    )


def ensure_server_properties(project_root: Path) -> tuple[Path, bool]:
    run_dir = project_root / "run"
    run_dir.mkdir(parents=True, exist_ok=True)
    properties_path = run_dir / "server.properties"
    content = smoke_server_properties_content()
    try:
        if properties_path.exists() and properties_path.read_text(encoding="utf-8") == content:
            return properties_path, False
    except OSError:
        pass
    properties_path.write_text(content, encoding="utf-8")
    return properties_path, True


def reader_thread(stream, output_queue: queue.Queue[object]) -> None:
    try:
        for line in iter(stream.readline, ""):
            output_queue.put(line)
    finally:
        output_queue.put(_READER_DONE)


def start_process(command: Sequence[str], project_root: Path) -> subprocess.Popen[str]:
    popen_kwargs: dict[str, object] = {}
    if os.name == "nt" and hasattr(subprocess, "CREATE_NEW_PROCESS_GROUP"):
        popen_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
    else:
        popen_kwargs["start_new_session"] = True
    return subprocess.Popen(
        list(command),
        cwd=str(project_root),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
        **popen_kwargs,
    )


def send_stop(process: subprocess.Popen[str]) -> bool:
    if process.poll() is not None or process.stdin is None:
        return False
    try:
        process.stdin.write("stop\n")
        process.stdin.flush()
        return True
    except (BrokenPipeError, OSError, ValueError):
        return False


def terminate_process_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        try:
            subprocess.run(
                ["taskkill", "/PID", str(process.pid), "/T", "/F"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            return
        except OSError:
            process.terminate()
            return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except OSError:
        process.terminate()


def kill_process_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        terminate_process_tree(process)
        return
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except OSError:
        process.kill()


def consume_output(
    output_queue: queue.Queue[object],
    log_lines: list[str],
    echo: bool,
    block_timeout: float | None = None,
) -> tuple[bool, bool, bool]:
    saw_reader_done = False
    saw_start = False
    saw_fatal = False
    while True:
        try:
            if block_timeout is None:
                item = output_queue.get_nowait()
            else:
                item = output_queue.get(timeout=block_timeout)
                block_timeout = None
        except queue.Empty:
            break
        if item is _READER_DONE:
            saw_reader_done = True
            continue
        raw_line = str(item)
        if echo:
            write_stdout(raw_line)
        line = normalize_log_line(raw_line)
        log_lines.append(line)
        if is_success_line(line):
            saw_start = True
        if scan_log_lines((line,), start_line=len(log_lines)):
            saw_fatal = True
    return saw_reader_done, saw_start, saw_fatal


def wait_with_output(
    process: subprocess.Popen[str],
    output_queue: queue.Queue[object],
    log_lines: list[str],
    deadline: float,
    echo: bool,
) -> tuple[bool, bool, bool]:
    saw_reader_done = False
    saw_start = False
    saw_fatal = False
    while process.poll() is None and time.monotonic() < deadline:
        remaining = max(0.0, min(0.25, deadline - time.monotonic()))
        reader_done, started, fatal = consume_output(output_queue, log_lines, echo, remaining)
        saw_reader_done = saw_reader_done or reader_done
        saw_start = saw_start or started
        saw_fatal = saw_fatal or fatal
    reader_done, started, fatal = consume_output(output_queue, log_lines, echo)
    saw_reader_done = saw_reader_done or reader_done
    saw_start = saw_start or started
    saw_fatal = saw_fatal or fatal
    return saw_reader_done, saw_start, saw_fatal


def run_server_smoke(
    command: Sequence[str],
    project_root: Path,
    startup_timeout: float,
    stop_timeout: float,
    echo: bool = True,
) -> SmokeOutcome:
    log_lines: list[str] = []
    output_queue: queue.Queue[object] = queue.Queue()
    process = start_process(command, project_root)
    if process.stdout is None:
        raise RuntimeError("runServer process did not provide stdout")
    thread = threading.Thread(target=reader_thread, args=(process.stdout, output_queue), daemon=True)
    thread.start()

    saw_start = False
    saw_fatal = False
    timed_out = False
    forced_shutdown = False
    stop_sent = False
    startup_deadline = time.monotonic() + startup_timeout

    while process.poll() is None and not saw_start and not saw_fatal:
        if time.monotonic() >= startup_deadline:
            timed_out = True
            break
        remaining = max(0.0, min(0.25, startup_deadline - time.monotonic()))
        _, started, fatal = consume_output(output_queue, log_lines, echo, remaining)
        saw_start = saw_start or started
        saw_fatal = saw_fatal or fatal

    _, started, fatal = consume_output(output_queue, log_lines, echo)
    saw_start = saw_start or started
    saw_fatal = saw_fatal or fatal

    if process.poll() is None:
        stop_sent = send_stop(process)
        grace = stop_timeout if saw_start else min(10.0, stop_timeout)
        deadline = time.monotonic() + grace
        _, started, fatal = wait_with_output(process, output_queue, log_lines, deadline, echo)
        saw_start = saw_start or started
        saw_fatal = saw_fatal or fatal

    if process.poll() is None:
        forced_shutdown = True
        terminate_process_tree(process)
        deadline = time.monotonic() + 10.0
        wait_with_output(process, output_queue, log_lines, deadline, echo)

    if process.poll() is None:
        kill_process_tree(process)
        try:
            process.wait(timeout=5.0)
        except subprocess.TimeoutExpired:
            pass

    consume_output(output_queue, log_lines, echo)
    thread.join(timeout=2.0)
    consume_output(output_queue, log_lines, echo)

    return SmokeOutcome(
        return_code=process.poll(),
        saw_start=saw_start,
        timed_out=timed_out,
        forced_shutdown=forced_shutdown,
        stop_sent=stop_sent,
        log_lines=log_lines,
    )


def print_failure_report(outcome: SmokeOutcome, startup_timeout: float, stop_timeout: float) -> None:
    print("Minecraft runtime smoke check failed:", file=sys.stderr)
    if outcome.timed_out:
        print(
            f"- Server startup signal was not observed within {startup_timeout:.0f} seconds.",
            file=sys.stderr,
        )
    elif not outcome.saw_start:
        print("- Server startup signal was not observed.", file=sys.stderr)
    if outcome.return_code not in (0, None):
        print(f"- Gradle runServer exited with code {outcome.return_code}.", file=sys.stderr)
    if outcome.forced_shutdown:
        print(
            f"- Process required forced termination after a stop grace period of {stop_timeout:.0f} seconds.",
            file=sys.stderr,
        )
    problems = scan_log_lines(outcome.log_lines)
    if problems:
        print("- Fatal log scan matches:", file=sys.stderr)
        for problem in problems[:20]:
            print(
                f"  - line {problem.line_number} [{problem.category}]: {problem.line}",
                file=sys.stderr,
            )
        if len(problems) > 20:
            print(f"  - ... {len(problems) - 20} more", file=sys.stderr)


def validate_outcome(outcome: SmokeOutcome, startup_timeout: float, stop_timeout: float) -> int:
    problems = scan_log_lines(outcome.log_lines)
    passed = (
        outcome.saw_start
        and not outcome.timed_out
        and not outcome.forced_shutdown
        and outcome.return_code == 0
        and not problems
    )
    if passed:
        print("Minecraft runtime smoke check passed.")
        print("- Server startup signal observed.")
        print("- Stop command completed cleanly.")
        print("- Fatal log scan found no matches.")
        return 0
    print_failure_report(outcome, startup_timeout, stop_timeout)
    return 1


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Start the Fabric runServer dev task, wait for the dedicated-server ready signal, "
            "stop it, and scan the combined output for fatal runtime errors."
        )
    )
    parser.add_argument("--project-root", type=Path, default=ROOT, help="Repository root to run from.")
    parser.add_argument("--gradle", default=None, help="Gradle wrapper command override.")
    parser.add_argument("--task", default="runServer", help="Gradle task to execute.")
    parser.add_argument(
        "--server-args",
        default="nogui",
        help="Arguments passed to the server via Gradle --args. Pass an empty string to omit.",
    )
    parser.add_argument(
        "--gradle-arg",
        action="append",
        default=[],
        help="Additional Gradle argument inserted before the runServer task. May be repeated.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_STARTUP_TIMEOUT_SECONDS,
        help="Seconds to wait for the server-ready signal.",
    )
    parser.add_argument(
        "--stop-timeout",
        type=float,
        default=DEFAULT_STOP_TIMEOUT_SECONDS,
        help="Seconds to wait after sending stop before forcing termination.",
    )
    parser.add_argument(
        "--accept-eula",
        action=argparse.BooleanOptionalAction,
        default=False,
        help=(
            "Create or update the ignored run/eula.txt file with eula=true before starting. "
            "Use only after reviewing and accepting the Minecraft EULA."
        ),
    )
    parser.add_argument("--no-echo", action="store_true", help="Do not mirror runServer output while running.")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    project_root = args.project_root.resolve()
    gradle_executable = args.gradle or default_gradle_executable()
    wrapper_path = project_root / gradle_executable
    if args.gradle is None and not wrapper_path.exists():
        print(f"Gradle wrapper not found: {wrapper_path}", file=sys.stderr)
        return 2
    if args.accept_eula:
        eula_path, changed = ensure_eula_accepted(project_root)
        action = "wrote" if changed else "found"
        print(f"{action} {eula_path.relative_to(project_root).as_posix()} with eula=true")
    properties_path, changed = ensure_server_properties(project_root)
    action = "wrote" if changed else "found"
    print(f"{action} {properties_path.relative_to(project_root).as_posix()} for smoke run")
    command = build_gradle_command(
        gradle_executable,
        args.task,
        args.server_args,
        tuple(args.gradle_arg),
    )
    print("Running Minecraft runtime smoke check:")
    print(f"- project root: {project_root}")
    print(f"- command: {format_command(command)}")
    print(f"- startup timeout: {args.timeout:.0f}s")
    try:
        outcome = run_server_smoke(
            command,
            project_root,
            startup_timeout=args.timeout,
            stop_timeout=args.stop_timeout,
            echo=not args.no_echo,
        )
    except FileNotFoundError as exc:
        print(f"Failed to start Gradle wrapper: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("Interrupted.", file=sys.stderr)
        return 130
    return validate_outcome(outcome, args.timeout, args.stop_timeout)


if __name__ == "__main__":
    raise SystemExit(main())
