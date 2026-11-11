#!/usr/bin/env python3
"""Start/stop an explicit disposable local support server. Never sources an env file."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import signal
import socket
import stat
import subprocess
import sys
import time

SERVER = Path(__file__).resolve().parents[1]
ROOT = SERVER.parent
RUN = SERVER / "build/local-support-web"
KEY_FILE = SERVER / ".env.local"


def parse_key(text):
    """Only one literal OPENAI_API_KEY assignment. No expansion, shell syntax or eval."""
    found = None
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        if not separator or name.strip() != "OPENAI_API_KEY" or found is not None:
            raise ValueError("Use one OPENAI_API_KEY assignment in the dedicated key file")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if not value or any(c.isspace() for c in value) or any(c in value for c in "`$;\x00"):
            raise ValueError("Enter a nonempty literal API key without shell syntax")
        found = value
    if found is None:
        raise ValueError("OPENAI_API_KEY is missing")
    return found


def isolated_environment(source):
    allowed = {"PATH", "HOME", "JAVA_HOME", "TMPDIR", "LANG", "LC_ALL", "DOCKER_HOST",
               "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH", "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE"}
    env = {name: value for name, value in source.items() if name in allowed}
    if env.get("DOCKER_HOST") and not env["DOCKER_HOST"].startswith("unix://"):
        raise ValueError("This launcher requires local Docker; remote DOCKER_HOST is not accepted")
    env["AWS_EC2_METADATA_DISABLED"] = "true"
    return env


def key_for_child(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError("Create the dedicated regular key file first")
    metadata = path.stat()
    if metadata.st_uid != os.getuid() or stat.S_IMODE(metadata.st_mode) & 0o077:
        raise ValueError("The key file must belong to the current user with mode 600")
    return parse_key(path.read_text())


def stop():
    if not RUN.is_dir():
        print("No local support run directory exists")
        return
    (RUN / "stop.request").touch()
    # Wait for our launcher lock, so a following start does not race shutdown.
    deadline = time.monotonic() + 30
    with (RUN / "launcher.lock").open("a") as lock:
        while True:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise ValueError("Stop requested, but shutdown is still running; check the start terminal before restarting") from None
                time.sleep(0.2)
    print("Dedicated local support launcher stopped")


def start(options):
    RUN.mkdir(parents=True, exist_ok=True)
    with (RUN / "launcher.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("A local support launcher is already running") from None
        with socket.socket() as probe:
            # Allow a same-port restart after closed HTTP connections enter TIME_WAIT.
            # A live listener still prevents this bind (SO_REUSEPORT is not enabled).
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(("127.0.0.1", options.port))
            except OSError:
                raise ValueError("The selected local port is unavailable; choose another --port") from None
        env = isolated_environment(os.environ)
        if options.enable_ai:
            if not options.model:
                raise ValueError("Use --model with the approved model before enabling AI")
            # The secret is read only here and passed through the child environment, never logged or placed in arguments.
            env["OPENAI_API_KEY"] = key_for_child(KEY_FILE)
        for name in ("stop.request", "ready.json", "model-usage.json"):
            (RUN / name).unlink(missing_ok=True)
        command = [str(SERVER / "gradlew"), "--no-daemon", "runLocalSupportWeb",
                   "-PsettyLocal.run-directory=" + str(RUN), "-PsettyLocal.port=" + str(options.port),
                   "-PsettyLocal.provider=openai", "-PsettyLocal.ai-enabled=" + str(options.enable_ai).lower()]
        if options.model:
            command.append("-PsettyLocal.model=" + options.model)
        if options.restore_from:
            restore = Path(options.restore_from).resolve()
            if not restore.is_file() or not restore.is_relative_to(SERVER / "build") or restore.suffix != ".sql":
                raise ValueError("Restore requires an existing dedicated snapshot under server/build")
            if stat.S_IMODE(restore.stat().st_mode) & 0o077:
                raise ValueError("The local snapshot must have mode 600")
            command.append("-PsettyLocal.restore-dump=" + str(restore))
        print("Starting isolated HTTP server; AI=" + str(options.enable_ai).lower(), flush=True)
        child = subprocess.Popen(command, cwd=SERVER, env=env, start_new_session=True)
        try:
            return child.wait()
        except KeyboardInterrupt:
            (RUN / "stop.request").touch()
            try:
                return child.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGTERM)
                return child.wait(timeout=15)
        finally:
            if child.poll() is None:
                os.killpg(child.pid, signal.SIGTERM)


def interrupted(_signal, _frame):
    raise KeyboardInterrupt


def main():
    global RUN
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    begin = sub.add_parser("start")
    begin.add_argument("--port", type=int, default=18080)
    begin.add_argument("--enable-ai", action="store_true", help="Requires approved real calls and an entered key")
    begin.add_argument("--model", help="Explicit approved model, for example gpt-6-luna")
    begin.add_argument("--restore-from", help="Restore an owned local SQL snapshot without reseeding or rotating credentials")
    sub.add_parser("stop").add_argument("--port", type=int, default=18080)
    sub.add_parser("status").add_argument("--port", type=int, default=18080)
    options = parser.parse_args()
    if not 1024 <= options.port <= 65535:
        raise ValueError("Choose a port between 1024 and 65535")
    if options.port != 18080:
        RUN = SERVER / ("build/local-support-web-" + str(options.port))
    if options.action == "stop":
        stop()
        return 0
    if options.action == "status":
        ready = RUN / "ready.json"
        if ready.exists():
            data = json.loads(ready.read_text())
            print(json.dumps({k: data[k] for k in ("baseUrl", "provider", "aiEnabled")}))
        else:
            print("NOT_READY")
        return 0
    if not 1024 <= options.port <= 65535:
        raise ValueError("Choose a port between 1024 and 65535")
    signal.signal(signal.SIGTERM, interrupted)
    return start(options)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, FileNotFoundError, PermissionError) as error:
        # Exceptions here contain only fixed guidance, not file contents or credential values.
        print("Local support setup: " + str(error), file=sys.stderr)
        sys.exit(2)
