"""Observe the deployed dev scheduler; no app/DB setting changes or external payment.

Run with sudo on the named dev instance. Baseline: before; follow-up: after.
Only freshly created, soft-deleted synthetic listings and their orders are changed.
Polling brackets describe first-to-last visible commits, not total scheduler time.
"""
import hashlib
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import sys
import time
from urllib.parse import urlsplit
import zipfile

PHASE = sys.argv[1]
assert PHASE in ("before", "after")
assert socket.gethostname() == "ip-10-0-20-53.ap-northeast-2.compute.internal"
settings = {}
for line in Path("/opt/setty/setty.env").read_text().splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key, value = line.strip().removeprefix("export ").split("=", 1)
        if key.startswith("SPRING_DATASOURCE_") or key == "SPRING_PROFILES_ACTIVE":
            settings[key] = shlex.split(value)[0]
url = urlsplit(settings["SPRING_DATASOURCE_URL"].removeprefix("jdbc:"))
assert settings["SPRING_PROFILES_ACTIVE"] == "dev"
assert url.hostname in ("localhost", "127.0.0.1") and url.path == "/setty"
env = dict(os.environ, MYSQL_PWD=settings["SPRING_DATASOURCE_PASSWORD"])
mysql = ["mysql", "--batch", "--raw", "--skip-column-names", "--skip-reconnect",
         "--skip-force", "-u", settings["SPRING_DATASOURCE_USERNAME"], "setty"]


def db(sql):
    return subprocess.check_output(mysql, input=sql, text=True, env=env).strip()


def code():
    jar = Path("/opt/setty/app/app.jar")
    with zipfile.ZipFile(jar) as z:
        batch = b"expireBatch" in z.read("BOOT-INF/classes/setty/platform/order/service/PendingOrderExpirationScheduler.class")
    assert batch == (PHASE == "after"), "Deployed implementation does not match phase"
    return {"jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            "batch": batch,
            "service": subprocess.check_output(["systemctl", "show", "setty-backend",
                        "--property=MainPID", "--property=ActiveEnterTimestamp",
                        "--property=ActiveState", "--property=SubState"], text=True).strip()}


root = Path("/tmp/setty-pending-expiration-" + PHASE + "-" + str(time.time_ns()))
root.mkdir()
prefix = "s2-expiry-" + hex(time.time_ns())[-8:]
before_code = code()
assert int(db("SELECT COUNT(*) FROM orders WHERE order_status='PENDING' AND pending_expires_at<=UTC_TIMESTAMP(6);")) == 0
assert int(db("SELECT COUNT(*) FROM members WHERE login_id LIKE 's2-expiry-%';")) == 0, "Previous probe data remains"
baseline_hashes = {t: hashlib.sha256(db("SELECT * FROM " + t + " ORDER BY id;").encode()).hexdigest()
                   for t in ("members", "listings", "orders")}
seller = buyer = None
ids = []
samples = []
result = {"phase": PHASE, "prefix": prefix, "root": str(root), "before_code": before_code,
          "poll_sleep_seconds": 0.1, "conditions": [1, 500, 500, 500],
          "scope": "Deployed scheduler; already-due synthetic orders on soft-deleted listings. No real six-minute wait, API, payment, SQL tracing or total job timing.",
          "baseline_hashes": baseline_hashes}
try:
    db("START TRANSACTION; INSERT INTO members(login_id,password,role,phone_number,address) VALUES ('" + prefix + "-s',REPEAT('!',60),'MEMBER','000-0000-0000','SYNTHETIC'),('" + prefix + "-b',REPEAT('!',60),'MEMBER','000-0000-0000','SYNTHETIC'); COMMIT;")
    seller = int(db("SELECT id FROM members WHERE login_id='" + prefix + "-s';"))
    buyer = int(db("SELECT id FROM members WHERE login_id='" + prefix + "-b';"))
    row = "(%d,'[SYNTHETIC EXPIRY PROBE]','dev scheduler probe',50000,10000,'DESK','A',80,50,70,'AVAILABLE',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))" % seller
    db("INSERT INTO listings(seller_id,title,description,price,delivery_fee,category,condition_grade,width_cm,height_cm,depth_cm,sale_status,has_purchase_request,created_at,updated_at,deleted_at) VALUES " + ",".join([row] * 502) + ";")
    ids = [int(x) for x in db("SELECT id FROM listings WHERE seller_id=%d ORDER BY id;" % seller).splitlines()]
    assert len(ids) == 502
    for run, due in enumerate([1, 500, 500, 500]):
        assert code() == before_code, "Deployment changed"
        db("START TRANSACTION; DELETE FROM orders WHERE buyer_id=%d; UPDATE listings SET has_purchase_request=FALSE WHERE seller_id=%d; COMMIT;" % (buyer, seller))
        target = ids[:due]
        values = ["(%d,%d,'PENDING','PENDING',UTC_TIMESTAMP(6)+INTERVAL 1 DAY)" % (i, buyer) for i in target]
        values += ["(%d,%d,'PENDING','PENDING',UTC_TIMESTAMP(6)+INTERVAL 1 DAY)" % (ids[500], buyer),
                   "(%d,%d,'PENDING','CONFIRMED',NULL)" % (ids[501], buyer)]
        db("START TRANSACTION; INSERT INTO orders(listing_id,buyer_id,delivery_status,order_status,pending_expires_at) VALUES " + ",".join(values) + "; UPDATE listings SET has_purchase_request=TRUE WHERE id IN (" + ",".join(map(str, target + ids[500:])) + "); COMMIT;")
        poll_sql = "SELECT SUM(o.order_status='EXPIRED'),SUM(o.order_status='PENDING'),SUM(o.order_status='CONFIRMED'),SUM(l.has_purchase_request=FALSE) FROM orders o JOIN listings l ON l.id=o.listing_id WHERE o.buyer_id=%d;" % buyer
        assert db(poll_sql) == "0\t%d\t1\t0" % (due + 1)
        armed = time.monotonic()
        db("UPDATE orders SET pending_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE buyer_id=%d AND listing_id IN (%s);" % (buyer, ",".join(map(str, target))))
        last_zero = (0.0, time.monotonic() - armed)
        first = complete = previous = None
        run_samples = []
        while time.monotonic() - armed < 120:
            start = time.monotonic() - armed
            state = list(map(int, db(poll_sql).split("\t")))
            end = time.monotonic() - armed
            point = {"start_s": start, "end_s": end, "state": state}
            run_samples.append(point)
            assert state[2] == 1 and state[1] >= 1
            if state[0] == 0:
                last_zero = (start, end)
            elif first is None:
                first = point
            if state[0] == due and state[3] == due:
                complete = point
                break
            previous = point
            time.sleep(0.1)
        assert complete is not None, "No completion within two scan intervals"
        assert complete["state"] == [due, 1, 1, due]
        assert code() == before_code, "Deployment changed"
        span_lower = max(0.0, (previous["start_s"] if previous else 0.0) - first["end_s"])
        span_upper = complete["end_s"] - last_zero[0]
        sample = {"run": run, "due": due, "completion_after_armed_s": complete["end_s"],
                  "first_visible_s": first["end_s"], "commit_span_lower_s": span_lower,
                  "commit_span_upper_s": span_upper, "polls": len(run_samples), "final_state": complete["state"]}
        samples.append(sample)
        (root / ("run-%d-polls.json" % run)).write_text(json.dumps(run_samples))
        print("DEV_EXPIRATION_RUN " + json.dumps(sample), flush=True)
        (root / "result.json").write_text(json.dumps(dict(result, samples=samples), indent=2))
    result["passed"] = True
finally:
    # Delete only IDs owned by this new synthetic cohort, never existing team data.
    if seller is not None and buyer is not None:
        assert int(db("SELECT COUNT(*) FROM orders WHERE buyer_id=%d AND listing_id NOT IN (SELECT id FROM listings WHERE seller_id=%d);" % (buyer, seller))) == 0
        db("START TRANSACTION; DELETE FROM orders WHERE buyer_id=%d; DELETE FROM listings WHERE seller_id=%d; DELETE FROM members WHERE id IN (%d,%d); COMMIT;" % (buyer, seller, buyer, seller))
        result["cleanup_complete"] = True
    after_hashes = {t: hashlib.sha256(db("SELECT * FROM " + t + " ORDER BY id;").encode()).hexdigest()
                    for t in ("members", "listings", "orders")}
    result["after_hashes"] = after_hashes
    result["existing_rows_unchanged"] = baseline_hashes == after_hashes
    result["samples"] = samples
    result["after_code"] = code()
    (root / "result.json").write_text(json.dumps(result, indent=2))
    print("DEV_EXPIRATION_RESULT " + json.dumps(result), flush=True)
