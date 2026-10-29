#!/usr/bin/env python3
"""Isolated, opt-in verification of the production JAR using two JVMs and MySQL."""

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def execute(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=180, **kwargs).stdout.strip()


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def literal(value):
    return "CONVERT(0x" + str(value).encode().hex() + " USING utf8mb4)"


def eventually(check, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = check()
            if last:
                return last
        except (OSError, urllib.error.URLError, subprocess.CalledProcessError):
            pass
        time.sleep(0.3)
    raise AssertionError(f"condition timed out after {timeout}s; last={last!r}")


class FakeToss(BaseHTTPRequestHandler):
    def read_body(self):
        if self.headers.get("Transfer-Encoding", "").lower() != "chunked":
            return self.rfile.read(int(self.headers.get("Content-Length", "0")))
        chunks = []
        while True:
            size = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
            if size == 0:
                while self.rfile.readline().strip():
                    pass
                return b"".join(chunks)
            chunks.append(self.rfile.read(size))
            assert self.rfile.read(2) == b"\r\n"

    def do_POST(self):
        if self.path != "/v1/payments/confirm":
            self.send_error(404)
            return
        body = json.loads(self.read_body())
        body.update(status="DONE", totalAmount=body["amount"], approvedAt="2026-10-29T12:00:00+09:00")
        encoded = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *_args):
        pass


class Lab:
    def __init__(self, args):
        self.args = args
        self.output = args.output.resolve()
        self.output.mkdir(parents=True, exist_ok=False)
        self.container = "setty-event-recovery-" + uuid.uuid4().hex[:10]
        self.container_created = False
        self.processes = []
        self.files = []
        self.lockers = []
        self.fake = ThreadingHTTPServer(("127.0.0.1", 0), FakeToss)
        self.result = {
            "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "commit": execute(["git", "rev-parse", "HEAD"]),
            "jar_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
            "blue_jar_sha256": hashlib.sha256((args.blue_jar or args.jar).read_bytes()).hexdigest(),
            "verifier_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "mysql_image": "mysql:8.4.11", "staleness_seconds": 15,
            "check_interval_seconds": 1, "resubmission_interval_seconds": 1,
            "cases": [], "status": "running",
        }

    def record(self, name, **data):
        self.result["cases"].append({"name": name, **data})
        print(json.dumps(self.result["cases"][-1], ensure_ascii=False), flush=True)

    def sql(self, sql):
        return execute(["docker", "exec", self.container, "mysql", "-uroot", "-plab-password",
                        "--batch", "--raw", "--skip-column-names", "setty_event_recovery_lab", "-e", sql])

    def start(self):
        threading.Thread(target=self.fake.serve_forever, daemon=True).start()
        execute(["docker", "run", "-d", "--name", self.container,
                 "--label", "setty.lab=event-recovery", "-p", "127.0.0.1::3306",
                 "-e", "MYSQL_ROOT_PASSWORD=lab-password", "-e", "MYSQL_DATABASE=setty_event_recovery_lab",
                 "mysql:8.4.11"])
        self.container_created = True
        eventually(lambda: self.sql("SELECT 1") == "1", timeout=120)
        mapping = execute(["docker", "port", self.container, "3306/tcp"])
        self.database_port = int(mapping.rsplit(":", 1)[1])
        self.green = self.jvm("green")
        self.green_pid = self.green[0].pid
        self.blue = self.jvm("blue-1")
        self.sql("""
            INSERT INTO members (id,login_id,password,role,phone_number,address,token)
            VALUES (1,'lab-seller','unused','MEMBER','010-0000-0001','서울시 가상구 출발로 1','lab-seller-token'),
                   (2,'lab-buyer','unused','MEMBER','010-0000-0002','서울시 가상구 도착로 2','lab-buyer-token');
        """)

    def jvm(self, name, resubmission_interval="PT1S"):
        port = free_port()
        # Inherit runtime essentials only; user application/DB/agent settings must not reach the lab.
        env = {key: value for key, value in os.environ.items()
               if key in {"PATH", "HOME", "TMPDIR", "LANG", "LC_ALL", "LC_CTYPE", "TZ", "JAVA_HOME"}}
        env.update({
            "SPRING_PROFILES_ACTIVE": "default", "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": str(port),
            "SPRING_DATASOURCE_URL": f"jdbc:mysql://127.0.0.1:{self.database_port}/setty_event_recovery_lab",
            "SPRING_DATASOURCE_USERNAME": "root", "SPRING_DATASOURCE_PASSWORD": "lab-password",
            "TOSS_SECRET_KEY": "lab-fake-key", "TOSS_BASE_URL": f"http://127.0.0.1:{self.fake.server_port}",
            "SETTY_EVENT_STALENESS_PUBLISHED": "PT15S", "SETTY_EVENT_STALENESS_PROCESSING": "PT15S",
            "SETTY_EVENT_STALENESS_RESUBMITTED": "PT15S", "SETTY_EVENT_STALENESS_CHECK_INTERVAL": "PT1S",
            "SETTY_EVENT_RESUBMISSION_INTERVAL": resubmission_interval, "SETTY_ADDRESS_BACKFILL_ENABLED": "false",
            "SETTY_LISTING_ZIP_ZONE_BACKFILL_ENABLED": "false", "AWS_EC2_METADATA_DISABLED": "true",
            "SPRING_MODULITH_EVENTS_REPUBLISH_OUTSTANDING_EVENTS_ON_RESTART": "false",
            "SETTY_FLYWAY_ADOPT_EXISTING": "false", "DELIVERY_FEE_ZONE_MODE": "dong",
            "PENDING_ORDER_EXPIRATION_SCAN_INTERVAL": "PT24H", "ORDER_COMPLETION_SCAN_INTERVAL": "PT24H",
        })
        log = (self.output / f"{name}.log").open("w")
        self.files.append(log)
        jar = self.args.blue_jar if name.startswith("blue") and self.args.blue_jar else self.args.jar
        process = subprocess.Popen([str(self.args.java), "-Xms128m", "-Xmx384m", "-jar", str(jar),
                                    "--setty.notification.sender=noop", "--management.otlp.metrics.export.enabled=false",
                                    "--setty.events.resubmission-interval=" + resubmission_interval],
                                   env=env, stdout=log, stderr=subprocess.STDOUT)
        self.processes.append(process)

        def ready():
            if process.poll() is not None:
                raise AssertionError(f"{name} exited; see {log.name}")
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/actuator/health", timeout=2) as response:
                return json.load(response).get("status") == "UP"
        eventually(ready, timeout=120)
        self.record("jvm_started", slot=name, pid=process.pid, port=port,
                    resubmission_interval=resubmission_interval)
        return process, port

    def seed(self, order):
        self.sql(f"""
            INSERT INTO listings (id,seller_id,title,description,price,delivery_fee,category,condition_grade,
                                  width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at)
            VALUES ({order},1,'가상 책상','검증 전용',100000,10000,'DESK','A',60,60,70,'AVAILABLE',true,NOW(6),NOW(6));
            INSERT INTO orders (id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at)
            VALUES ({order},{order},2,'PENDING','PENDING',DATE_ADD(NOW(6),INTERVAL 10 MINUTE));
        """)

    def pay(self, order):
        payload = {"orderId": f"{order}_lab", "paymentKey": f"lab-key-{order}", "amount": 110000}
        request = urllib.request.Request(f"http://127.0.0.1:{self.blue[1]}/api/payments/confirm",
                                         data=json.dumps(payload).encode(), headers={
                                             "Content-Type": "application/json", "Authorization": "Bearer lab-buyer-token"})
        with urllib.request.urlopen(request, timeout=10) as response:
            assert response.status == 200
            return json.load(response)

    def publications(self, order, event_type=None):
        condition = f"JSON_EXTRACT(SERIALIZED_EVENT,'$.orderId') = {order}"
        if event_type:
            condition += f" AND EVENT_TYPE = {literal(event_type)}"
        text = self.sql(f"""SELECT JSON_OBJECT('id',ID,'type',EVENT_TYPE,'listener',LISTENER_ID,
                    'event',SERIALIZED_EVENT,'status',STATUS,'attempts',COMPLETION_ATTEMPTS)
                    FROM EVENT_PUBLICATION WHERE {condition} ORDER BY ID""")
        return [json.loads(line) for line in text.splitlines()]

    def snapshot(self, order):
        return json.loads(self.sql(f"""SELECT JSON_OBJECT(
            'order',(SELECT order_status FROM orders WHERE id={order}),
            'payment',(SELECT status FROM payments WHERE order_id={order}),
            'payments',(SELECT COUNT(*) FROM payments WHERE order_id={order}),
            'deliveries',(SELECT COUNT(*) FROM delivery WHERE order_id={order}),
            'seller_settlements',(SELECT COUNT(*) FROM settlements WHERE order_id={order} AND payee_type='SELLER'))"""))

    def recovered(self, order):
        state = self.snapshot(order)
        expected = {"order": "CONFIRMED", "payment": "DONE", "payments": 1,
                    "deliveries": 1, "seller_settlements": 1}
        return state == expected and not self.publications(order)

    def lock(self, select):
        marker = "DO SLEEP(180) /* " + uuid.uuid4().hex + " */"
        log = (self.output / f"lock-{len(self.lockers)}.log").open("w")
        self.files.append(log)
        process = subprocess.Popen(["docker", "exec", self.container, "mysql", "-uroot", "-plab-password",
                                    "--batch", "setty_event_recovery_lab", "-e",
                                    "BEGIN; " + select + "; " + marker + "; ROLLBACK;"],
                                   stdout=log, stderr=subprocess.STDOUT)
        self.lockers.append(process)
        connection = eventually(lambda: self.sql("SELECT ID FROM information_schema.PROCESSLIST WHERE INFO="
                                                  + literal(marker)))
        return int(connection)

    def unlock(self, connection):
        self.sql(f"KILL {connection}")

    def processing(self, order, event_type, min_attempts=1):
        rows = self.publications(order, event_type)
        if event_type == "setty.common.OrderConfirmed":
            # OrderConfirmed fans out to delivery and settlement; probe the blocked delivery listener explicitly.
            rows = [row for row in rows if row["listener"].startswith("setty.delivery.event.DeliveryEventListener.")]
        return next((row for row in rows if row["status"] == "PROCESSING"
                     and row["attempts"] >= min_attempts), None)

    def crash(self, order, event_type, lock_query, name):
        self.seed(order)
        connection = self.lock(lock_query)
        self.pay(order)
        publication = eventually(lambda: self.processing(order, event_type), timeout=10)
        before = self.snapshot(order)
        assert before["payment"] == "DONE"
        if event_type == "setty.common.PaymentCompleted":
            assert before["order"] == "PENDING" and before["deliveries"] == 0
        else:
            assert before["order"] == "CONFIRMED" and before["deliveries"] == 0
        killed_pid = self.blue[0].pid
        self.blue[0].kill()
        self.blue[0].wait(timeout=10)
        started = time.monotonic()
        self.unlock(connection)
        eventually(lambda: self.recovered(order))
        assert self.green[0].pid == self.green_pid and self.green[0].poll() is None
        self.record(name, order_id=order, killed_pid=killed_pid, green_pid=self.green_pid,
                    before=before, interrupted_publication=publication,
                    after=self.snapshot(order), recovery_seconds=round(time.monotonic() - started, 3))
        return publication

    def insert_publication(self, template, attempts=1):
        identifier = str(uuid.uuid4())
        self.sql(f"""INSERT INTO EVENT_PUBLICATION
            (ID,LISTENER_ID,EVENT_TYPE,SERIALIZED_EVENT,PUBLICATION_DATE,STATUS,COMPLETION_ATTEMPTS)
            VALUES ({literal(identifier)},{literal(template['listener'])},{literal(template['type'])},
                    {literal(template['event'])},DATE_SUB(NOW(6),INTERVAL 1 DAY),'FAILED',{attempts})""")
        return identifier

    def verify(self):
        self.start()
        payment = self.crash(1001, "setty.common.PaymentCompleted",
                             "SELECT id FROM orders WHERE id=1001 FOR UPDATE", "payment_listener_sigkill")
        self.blue = self.jvm("blue-2")
        delivery = self.crash(1002, "setty.common.OrderConfirmed",
                              "SELECT order_id FROM delivery_order_decision WHERE order_id=1002 FOR UPDATE",
                              "delivery_listener_sigkill")
        # Blue can process the original event, but only Green can start its resubmission in this case.
        self.blue = self.jvm("blue-3", resubmission_interval="PT24H")
        self.seed(1003)
        connection = self.lock("SELECT id FROM orders WHERE id=1003 FOR UPDATE")
        self.pay(1003)
        eventually(lambda: self.processing(1003, "setty.common.PaymentCompleted"), timeout=10)
        # Blue's original listener is still blocked while the other JVM retries the same publication.
        overlap = eventually(lambda: self.processing(1003, "setty.common.PaymentCompleted", min_attempts=2))
        assert self.blue[0].poll() is None and self.green[0].poll() is None
        before = self.snapshot(1003)
        self.unlock(connection)
        eventually(lambda: self.recovered(1003))
        self.record("live_listener_overlap", publication=overlap, original_pid=self.blue[0].pid,
                    retry_pid=self.green_pid, before=before, after=self.snapshot(1003))

        for template in [payment, payment, delivery, delivery]:
            self.insert_publication(template)
        eventually(lambda: self.recovered(1001) and self.recovered(1002))
        self.record("duplicate_payment_and_order_events", copies=4,
                    orders={str(order): self.snapshot(order) for order in [1001, 1002, 1003]})

        exhausted = self.insert_publication(payment, attempts=5)
        time.sleep(3)
        remaining = self.sql("SELECT CONCAT(STATUS,':',COMPLETION_ATTEMPTS) FROM EVENT_PUBLICATION WHERE ID="
                             + literal(exhausted))
        assert remaining == "FAILED:5", remaining
        self.record("retry_exhaustion", remaining=remaining, observation_seconds=3)
        self.result["status"] = "passed"

    def close(self):
        for process in self.processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
        if self.container_created:
            logs = subprocess.run(["docker", "logs", self.container], text=True, capture_output=True, timeout=15)
            (self.output / "mysql.log").write_text(logs.stdout + logs.stderr)
            cleanup = subprocess.run(["docker", "rm", "-f", "-v", self.container], capture_output=True, timeout=30)
            self.result["container_removed"] = cleanup.returncode == 0
            if cleanup.returncode:
                self.result.update(status="failed", cleanup_error=cleanup.stderr.decode(errors="replace"))
        for process in self.lockers:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.terminate()
        self.fake.shutdown()
        self.fake.server_close()
        for file in self.files:
            file.close()
        self.result["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (self.output / "result.json").write_text(json.dumps(self.result, ensure_ascii=False, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--blue-jar", type=Path, help="Optional baseline Blue JAR for mixed-version verification")
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.jar = args.jar.resolve(strict=True)
    if args.blue_jar:
        args.blue_jar = args.blue_jar.resolve(strict=True)
    args.java = args.java.resolve(strict=True)
    lab = Lab(args)
    try:
        lab.verify()
    except BaseException as error:
        lab.result.update(status="failed", error=repr(error))
        raise
    finally:
        lab.close()
    if lab.result["status"] != "passed":
        raise AssertionError("verification or resource cleanup failed; see result.json")


if __name__ == "__main__":
    main()
