#!/usr/bin/env python3
"""격리된 스키마 실험의 명시적 단계 실행. DB나 실행 기록을 삭제하지 않는다."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
import urllib.error
import urllib.request
import uuid
import zipfile
import zlib


REPO = Path(__file__).resolve().parents[2]
SUPPORT = Path(__file__).resolve().parent
VERSIONS = {"C0": 10, "C1": 11, "C2": 11, "C3": 12, "C4": 13}
PORTS = {"blue": 18080, "green": 18081}
TOKEN = "schema-lab-seller-token"


def now():
    return datetime.now(timezone.utc).isoformat()


def valid_name(value):
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,39}", value):
        raise ValueError("실행 ID는 소문자·숫자로 시작하고 소문자·숫자·하이픈 40자 이하여야 합니다.")
    return value


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".next")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def migrations_in(jar):
    with zipfile.ZipFile(jar) as archive:
        return sorted((name for name in archive.namelist()
                       if re.fullmatch(r"BOOT-INF/classes/db/migration/V\d+__[^/]+\.sql", name)),
                      key=lambda name: int(re.search(r"/V(\d+)__", name).group(1)))


def check_migrations(version, names):
    numbers = [int(re.search(r"/V(\d+)__", name).group(1)) for name in names]
    if numbers != list(range(1, VERSIONS[version] + 1)):
        raise ValueError(f"{version}에는 V1~V{VERSIONS[version]}만 있어야 합니다: {numbers}")


def png_fixture():
    def chunk(kind, data):
        return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\xff\xff\xff")) + chunk(b"IEND", b""))


class Lab:
    def __init__(self, run_id, root=None):
        self.run_id = valid_name(run_id)
        self.root = root or REPO / ".local" / "schema-lab" / run_id
        self.state_path = self.root / "state.json"
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else None

    def require_run(self):
        if self.state is None:
            raise ValueError("먼저 init을 실행하세요.")

    def compose(self, *args, capture=False, input_text=None, check=True):
        self.require_run()
        environment = dict(os.environ, SCHEMA_LAB_RUNTIME_ROOT=self.state["runtime_root"],
                           SCHEMA_LAB_BLUE_VERSION=self.state["slots"].get("blue", {}).get("version", "unset"),
                           SCHEMA_LAB_GREEN_VERSION=self.state["slots"].get("green", {}).get("version", "unset"))
        return subprocess.run(["docker", "compose", "--profile", "slots", "-p", "setty-schema-" + self.run_id,
                               "-f", str(SUPPORT / "compose.yml"), *args],
                              env=environment, text=True, input=input_text, capture_output=capture, check=check)

    def save(self):
        write_json(self.state_path, self.state)

    def query(self, sql):
        return self.compose("exec", "-T", "db", "sh", "-c",
                            'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" '
                            '"$MYSQL_DATABASE" --batch --raw --skip-column-names',
                            capture=True, input_text=sql).stdout.strip()

    def running(self):
        return self.compose("ps", "--status", "running", "--services", capture=True).stdout.split()

    def init(self):
        if self.state is None:
            self.root.mkdir(parents=True, exist_ok=True)
            for directory in ("jars", "evidence"):
                (self.root / directory).mkdir(exist_ok=True, parents=True)
            self.state = {"run_id": self.run_id, "created_at": now(), "active": None, "slots": {}}
            (self.root / "fixture.png").write_bytes(png_fixture())
            self.save()
        if "runtime_root" not in self.state:
            self.state["runtime_root"] = tempfile.mkdtemp(prefix="setty-schema-" + self.run_id + "-", dir="/tmp")
            self.save()
        runtime = Path(self.state["runtime_root"])
        for directory in ("slots/blue", "slots/green", "nginx"):
            (runtime / directory).mkdir(exist_ok=True, parents=True)
        shutil.copyfile(SUPPORT / "nginx.conf", runtime / "nginx.conf")
        self.compose("up", "-d", "--wait", "db")
        print(self.root)

    def build(self, version, offline=False):
        self.require_run()
        target = self.root / "jars" / f"{version}.jar"
        if target.exists():
            raise ValueError(f"기존 산출물은 덮어쓰지 않습니다: {target}")
        dirty = subprocess.check_output(["git", "status", "--porcelain", "--", "server/src/main",
                                         "server/build.gradle"], cwd=REPO, text=True).strip()
        if dirty:
            raise ValueError("앱 소스를 커밋한 뒤 버전별 JAR를 만드세요.\n" + dirty)
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()
        environment = dict(os.environ)
        java_home = environment.get("SCHEMA_LAB_JAVA_HOME")
        if not java_home and sys.platform == "darwin":
            java_home = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
        if java_home:
            environment["JAVA_HOME"] = java_home
        command = ["./gradlew", "bootJar"] + (["--offline"] if offline else [])
        subprocess.run(command, cwd=REPO / "server", env=environment, check=True)
        if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip() != commit:
            raise ValueError("빌드 중 HEAD가 변경되었습니다. 산출물은 등록하지 않습니다.")
        candidates = [p for p in (REPO / "server/build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
        if len(candidates) != 1:
            raise ValueError(f"bootJar 산출물을 하나로 특정할 수 없습니다: {candidates}")
        names = migrations_in(candidates[0])
        check_migrations(version, names)
        shutil.copyfile(candidates[0], target)
        manifest = {"version": version, "commit": commit, "built_at": now(),
                    "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "migrations": names}
        write_json(target.with_suffix(".json"), manifest)
        print(json.dumps(manifest, indent=2))

    def request(self, method, path, label, payload=None, image=False, slot=None):
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", label):
            raise ValueError("요청 라벨에는 영문·숫자·하이픈·밑줄만 사용하세요.")
        probe = f"{self.run_id}-{label}-{uuid.uuid4().hex[:8]}"
        base = f"http://127.0.0.1:{PORTS[slot]}" if slot else "http://127.0.0.1:18000"
        headers = {"X-Request-Id": probe, "Connection": "close"}
        data = None
        if payload is not None:
            boundary = "schema-lab-" + uuid.uuid4().hex
            data = (f'--{boundary}\r\nContent-Disposition: form-data; name="request"\r\n'
                    'Content-Type: application/json\r\n\r\n').encode() + json.dumps(payload).encode() + b"\r\n"
            if image:
                data += (f'--{boundary}\r\nContent-Disposition: form-data; name="images"; '
                         'filename="fixture.png"\r\nContent-Type: image/png\r\n\r\n').encode()
                data += (self.root / "fixture.png").read_bytes() + b"\r\n"
            data += f"--{boundary}--\r\n".encode()
            headers.update({"Content-Type": "multipart/form-data; boundary=" + boundary,
                            "Authorization": "Bearer " + TOKEN})
        started = now()
        try:
            with urllib.request.urlopen(urllib.request.Request(base + path, data=data, headers=headers,
                                                               method=method), timeout=10) as response:
                status, response_headers, body = response.status, dict(response.headers), response.read().decode()
        except urllib.error.HTTPError as error:
            write_json(self.root / "evidence" / f"{probe}.json",
                       {"probe_id": probe, "started_at": started, "status": error.code,
                        "body": error.read().decode()})
            raise
        except OSError as error:
            write_json(self.root / "evidence" / f"{probe}.json",
                       {"probe_id": probe, "started_at": started, "status": 0,
                        "error": f"{type(error).__name__}: {error}"})
            raise
        record = {"probe_id": probe, "started_at": started, "completed_at": now(), "url": base + path,
                  "method": method, "status": status, "headers": response_headers,
                  "body": json.loads(body) if body else None}
        write_json(self.root / "evidence" / f"{probe}.json", record)
        return record

    def healthy(self, slot):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{PORTS[slot]}/actuator/health", timeout=3) as response:
                    if response.status == 200 and json.load(response).get("status") == "UP":
                        return
            except (OSError, ValueError):
                pass
            time.sleep(1)
        raise ValueError(f"{slot} 기동 확인 시간 초과. logs 명령으로 확인하세요.")

    def start(self, slot, version):
        self.require_run()
        if slot in self.running():
            raise ValueError(f"실행 중인 {slot}을 덮어쓰지 않습니다. 비활성 슬롯을 먼저 stop하세요.")
        source = self.root / "jars" / f"{version}.jar"
        manifest = json.loads(source.with_suffix(".json").read_text())
        if hashlib.sha256(source.read_bytes()).hexdigest() != manifest["sha256"]:
            raise ValueError("보관한 JAR의 SHA-256이 변경되었습니다.")
        check_migrations(version, migrations_in(source))
        runtime = Path(self.state["runtime_root"])
        (runtime / "slots" / slot).mkdir(exist_ok=True, parents=True)
        shutil.copyfile(source, runtime / "slots" / slot / "app.jar")
        self.state["slots"][slot] = manifest
        self.save()
        self.compose("up", "-d", "--no-deps", "--force-recreate", slot)
        self.healthy(slot)
        print(f"{slot}: {version}, ready")

    def route(self, slot):
        self.require_run()
        self.healthy(slot)
        config = Path(self.state["runtime_root"]) / "nginx/active-upstream.conf"
        previous = config.read_text() if config.exists() else None
        temporary = config.with_suffix(".next")
        temporary.write_text(f"upstream setty_schema_lab {{ server {slot}:8080; }}\n")
        temporary.replace(config)
        try:
            if "nginx" in self.running():
                self.compose("exec", "-T", "nginx", "nginx", "-t")
                self.compose("exec", "-T", "nginx", "nginx", "-s", "reload")
            else:
                self.compose("up", "-d", "--no-deps", "nginx")
            container = self.compose("ps", "-q", slot, capture=True).stdout.strip()
            inspected = json.loads(subprocess.check_output(["docker", "inspect", container], text=True))[0]
            expected = {network["IPAddress"] + ":8080"
                        for network in inspected["NetworkSettings"]["Networks"].values()}
            deadline = time.monotonic() + 15
            while True:
                try:
                    probe = self.request("GET", "/actuator/health", "route-" + slot)
                    actual = next((v for k, v in probe["headers"].items()
                                   if k.lower() == "x-schema-lab-upstream"), None)
                    if actual in expected:
                        break
                except OSError:
                    pass
                if time.monotonic() >= deadline:
                    raise ValueError("실제 upstream이 신규 슬롯으로 바뀌지 않았습니다.")
                time.sleep(0.3)
        except Exception:
            if previous is not None:
                config.write_text(previous)
                self.compose("exec", "-T", "nginx", "nginx", "-s", "reload", check=False)
            raise
        self.state["active"] = slot
        self.save()
        print(f"active: {slot} / {self.state['slots'][slot]['version']}, upstream: {actual}")

    def data(self):
        path = self.root / "data.json"
        return json.loads(path.read_text()) if path.exists() else {}

    def create(self, label, title, slot=None):
        data = self.data()
        if label in data:
            raise ValueError(f"이미 생성한 라벨입니다: {label}")
        payload = {"title": title, "description": "schema compatibility lab", "price": 50000,
                   "category": "CHAIR", "conditionGrade": "A",
                   "dimensions": {"widthCm": 40, "depthCm": 40, "heightCm": 80}}
        response = self.request("POST", "/api/listings", label + "-create", payload, image=True, slot=slot)
        if response["status"] != 201:
            raise ValueError("등록 응답이 201이 아닙니다.")
        data[label] = {"id": response["body"]["listingId"], "title": title.strip(), "created_at": now()}
        write_json(self.root / "data.json", data)
        print(json.dumps(data[label], ensure_ascii=False))

    def update(self, label, title, slot=None):
        data = self.data()
        row = data[label]
        detail = self.request("GET", f"/api/listings/{row['id']}", label + "-before-update", slot=slot)["body"]
        payload = {key: detail[key] for key in ("description", "price", "category", "conditionGrade", "dimensions")}
        payload.update(title=title, retainedImageIds=[image["id"] for image in detail["images"]])
        response = self.request("PUT", f"/api/listings/{row['id']}", label + "-update", payload, slot=slot)
        if response["status"] != 204:
            raise ValueError("수정 응답이 204가 아닙니다.")
        row["title"] = title.strip()
        write_json(self.root / "data.json", data)
        print(json.dumps(row, ensure_ascii=False))

    def verify(self, db_version, dual=False, slot=None):
        self.require_run()
        target = slot or self.state["active"]
        if not target:
            raise ValueError("먼저 route로 활성 슬롯을 지정하세요.")
        version = self.query("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;")
        if version != str(db_version):
            raise ValueError(f"DB 버전이 {db_version}이 아닙니다: {version}")
        expected = {row["id"]: row["title"] for row in self.data().values()}
        response = self.request("GET", "/api/listings", "verify-list", slot=slot)
        actual = {row["id"]: row["title"] for row in response["body"]["items"]}
        if actual != expected:
            raise ValueError(f"목록 불일치: expected={expected}, actual={actual}")
        for listing_id, title in expected.items():
            detail = self.request("GET", f"/api/listings/{listing_id}", "verify-detail", slot=slot)
            if detail["body"]["title"] != title:
                raise ValueError(f"상세 제목 불일치: {listing_id}")
        column = "title" if self.state["slots"][target]["version"] in ("C0", "C1") else "display_title"
        rows = self.query(f"SELECT id, {column} FROM listings ORDER BY id;")
        stored = {int(line.split("\t", 1)[0]): line.split("\t", 1)[1] for line in rows.splitlines() if line}
        if stored != expected:
            raise ValueError(f"DB 값 불일치: {stored}")
        if dual and self.query("SELECT COUNT(*) FROM listings WHERE NOT "
                               "(CAST(display_title AS BINARY) <=> CAST(title AS BINARY));") != "0":
            raise ValueError("두 컬럼의 값이 다릅니다.")
        result = {"at": now(), "slot": target, "version": self.state["slots"][target]["version"],
                  "db_version": db_version, "rows": len(expected), "dual": dual, "data": expected}
        write_json(self.root / "evidence" / f"verify-{uuid.uuid4().hex[:12]}.json", result)
        print(json.dumps(result, ensure_ascii=False, indent=2))

    def seed(self):
        if self.state["active"] is None or self.state["slots"][self.state["active"]]["version"] != "C0":
            raise ValueError("기준 데이터는 활성 C0에서만 생성합니다.")
        if self.query("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;") != "10":
            raise ValueError("기준 DB는 V10이어야 합니다.")
        if self.query("SELECT COUNT(*) FROM listings;") != "0":
            raise ValueError("빈 실험 DB에서만 기준 데이터를 만듭니다. 기존 데이터를 삭제하지 않습니다.")
        self.query("INSERT INTO members (login_id,password,role,phone_number,address,dong_code,token) VALUES "
                   "('schema_lab_seller',REPEAT('x',60),'MEMBER','010-0000-0000','schema lab address',"
                   "'1111053000','schema-lab-seller-token');")
        self.create("E1", "lab-E1-initial")
        self.create("E2", "lab-E2-initial")
        self.update("E2", "lab-E2-initial")
        self.verify(10)

    def status(self):
        self.require_run()
        print(json.dumps(self.state, ensure_ascii=False, indent=2))
        self.compose("ps")
        if "db" not in self.running():
            print("DB: stopped (volume retained)")
            return
        tables = self.query("SHOW TABLES LIKE 'flyway_schema_history';")
        print("DB version:", self.query("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;")
              if tables else "not initialized")

    def stop(self, slot):
        if self.state["active"] == slot:
            raise ValueError("활성 슬롯은 중지하지 않습니다. 다른 슬롯으로 먼저 전환하세요.")
        self.compose("stop", slot)

    def logs(self):
        self.require_run()
        content = self.compose("logs", "--no-color", capture=True).stdout
        path = self.root / "evidence" / f"containers-{uuid.uuid4().hex[:8]}.log"
        path.write_text(content)
        print(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("init", "seed", "status", "logs", "sql", "halt"):
        commands.add_parser(name)
    build = commands.add_parser("build")
    build.add_argument("version", choices=VERSIONS)
    build.add_argument("--offline", action="store_true")
    start = commands.add_parser("start")
    start.add_argument("slot", choices=PORTS)
    start.add_argument("version", choices=VERSIONS)
    for name in ("route", "stop"):
        commands.add_parser(name).add_argument("slot", choices=PORTS)
    for name in ("create", "update"):
        command = commands.add_parser(name)
        command.add_argument("label")
        command.add_argument("title")
        command.add_argument("--slot", choices=PORTS)
    verify = commands.add_parser("verify")
    verify.add_argument("--db-version", required=True, type=int)
    verify.add_argument("--dual", action="store_true")
    verify.add_argument("--slot", choices=PORTS)
    args = parser.parse_args()
    lab = Lab(args.run_id)
    if args.command != "init":
        lab.require_run()
    if args.command in ("init", "seed", "status", "logs"):
        getattr(lab, args.command)()
    elif args.command == "build":
        lab.build(args.version, args.offline)
    elif args.command == "start":
        lab.start(args.slot, args.version)
    elif args.command in ("route", "stop"):
        getattr(lab, args.command)(args.slot)
    elif args.command in ("create", "update"):
        getattr(lab, args.command)(args.label, args.title, args.slot)
    elif args.command == "verify":
        lab.verify(args.db_version, args.dual, args.slot)
    elif args.command == "sql":
        print(lab.query(sys.stdin.read()))
    elif args.command == "halt":
        lab.compose("stop", "nginx")
        lab.compose("stop", "blue", "green")
        lab.compose("stop", "db")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"실험 단계 실패: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr, file=sys.stderr)
        sys.exit(1)
