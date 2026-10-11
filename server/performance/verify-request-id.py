"""Gatling 요청 ID CSV 검증. 임시 localhost 서버만 사용하며 dev에 요청하지 않는다."""

import csv
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parents[1]
observed = []
state = {"active": 0, "max_active": 0}
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        with lock:
            number = len(observed) + 1
            request_id = "" if number == 21 else str(uuid.uuid4())
            if number == 22:
                request_id = 'csv,"quote"'
            row = {"request_id": request_id, "started": time.monotonic(), "path": self.path}
            observed.append(row)
            state["active"] += 1
            state["max_active"] = max(state["max_active"], state["active"])
        payload = json.dumps({"items": [{"id": n} for n in range(100)]}).encode()
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            if request_id:
                self.send_header("X-Request-Id", request_id)
            self.end_headers()
            self.wfile.write(payload)
            self.wfile.flush()
        finally:
            with lock:
                row["completed"] = time.monotonic()
                row["bytes"] = len(payload)
                state["active"] -= 1

    def log_message(self, *args):
        pass


def main():
    output = Path(tempfile.mkdtemp(prefix="setty-request-id-verification-"))
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    csv_dir = ROOT / "build/reports/listing-count"
    before = set(csv_dir.glob("*.csv"))
    env = dict(os.environ, LISTING_BASE_URL=f"http://127.0.0.1:{server.server_port}", LISTING_COUNT="100")
    print(f"localhost only; 120 sequential requests; output: {output}", flush=True)
    try:
        with (output / "gatling.log").open("w") as log:
            result = subprocess.run(
                ["./gradlew", "gatlingRun", "--simulation", "setty.performance.ListingCountSimulation",
                 "--run-description", "LOCAL-request-id-verification"],
                cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=240)
        assert result.returncode == 0, (output / "gatling.log").read_text()[-3000:]
        created = set(csv_dir.glob("*.csv")) - before
        assert len(created) == 1, created
        path = created.pop()
        with path.open() as file:
            rows = list(csv.DictReader(file))
        assert len(rows) == len(observed) == 120
        assert state["max_active"] == 1, state
        gaps = [b["started"] - a["completed"] for a, b in zip(observed, observed[1:])]
        assert min(gaps) >= 0.95, min(gaps)
        for index, (row, sent) in enumerate(zip(rows, observed)):
            assert row["request_id"] == sent["request_id"], (index, row, sent)
            assert row["phase"] == ("listings-warmup" if index < 20 else "listings-measurement")
            assert row["failed"] == "false"
            assert int(row["request_number"]) == index + 1
            assert int(row["response_body_bytes"]) == sent["bytes"]
            assert int(row["response_time_ms"]) >= 0
            assert sent["path"] == "/dev/api/listings"
        summary = {"passed": True, "requests": 120, "warmup": 20, "measurement": 100,
                   "missing_header_row": 21, "quoted_header_row": 22, "max_concurrent": 1,
                   "minimum_gap_seconds": min(gaps), "csv": str(path)}
        (output / "result.json").write_text(json.dumps(summary, indent=2))
        print(json.dumps(summary), flush=True)
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
