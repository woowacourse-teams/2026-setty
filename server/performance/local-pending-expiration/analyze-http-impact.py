"""Summarize paired localhost measurements; compare matched relative-time windows."""
import json
import math
import statistics
import sys
from pathlib import Path


def percentile(values, quantile):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * quantile) - 1)]


def summarize(rows):
    values = [row["client_ms"] for row in rows]
    if not values:
        return {"n": 0, "median_ms": None, "p95_ms": None, "max_ms": None, "errors": 0}
    return {
        "n": len(rows),
        "median_ms": statistics.median(values),
        "p95_ms": percentile(values, 0.95),
        "max_ms": max(values),
        "errors": sum("error" in row or row.get("status") not in (200, 201) for row in rows),
    }


def main():
    output = Path(sys.argv[1])
    requests = json.loads((output / "requests.json").read_text())
    runs = json.loads((output / "runs.json").read_text())
    pools = json.loads((output / "pool-samples.json").read_text())
    metadata = json.loads((output / "metadata.json").read_text())
    assert len(runs) == 6
    measurements = [r for r in requests if r["condition"] != "warmup"]
    assert len(measurements) == 3_000
    assert len({r["request_id"] for r in measurements}) == len(measurements)
    assert all("error" not in r and r["status"] == (200 if r["kind"] == "list" else 201) for r in measurements)
    by_run = []
    matched = []
    for run in runs:
        pair, condition = run["pair"], run["condition"]
        treatment = next(r for r in runs if r["pair"] == pair and r["condition"] == "with_expiration")
        window = treatment["expiration"]
        rows = [r for r in measurements if r["pair"] == pair and r["condition"] == condition]
        pool = [r for r in pools if r["pair"] == pair and r["condition"] == condition]
        launch_delay = [r["journey_start_ms"] - r["scheduled_journey_ms"] for r in rows if r["kind"] == "list"]
        entry = dict(run)
        entry["pool_max_active"] = max(r["active"] for r in pool)
        entry["pool_max_waiting"] = max(r["waiting"] for r in pool)
        entry["pool_max_total"] = max(r["total"] for r in pool)
        entry["launch_delay_ms_p95"] = percentile(launch_delay, 0.95)
        entry["launch_delay_ms_max"] = max(launch_delay)
        entry["full_run"] = {kind: summarize([r for r in rows if r["kind"] == kind]) for kind in ("list", "create")}
        entry["matched_expiration_window"] = {}
        for kind in ("list", "create"):
            selected = [r for r in rows if r["kind"] == kind and window["start_ms"] <= r["start_ms"] < window["end_ms"]]
            entry["matched_expiration_window"][kind] = summarize(selected)
            matched.extend(selected)
        by_run.append(entry)
    pooled = {}
    for condition in ("without_expiration", "with_expiration"):
        pooled[condition] = {
            kind: summarize([r for r in matched if r["condition"] == condition and r["kind"] == kind])
            for kind in ("list", "create")
        }
    result = {
        "metadata": metadata,
        "total_measurement_requests": len(measurements),
        "total_errors": 0,
        "per_run": by_run,
        "pooled_matched_window": pooled,
        "interpretation_limits": [
            "Percentiles use nearest-rank; pooled windows combine three matched pairs, not independent user samples.",
            "Window samples are requests starting while expiration ran; other requests in flight at job start are excluded.",
            "Same-host JVM/CPU/GC and database state can affect both HTTP and batch durations. These are not production SLA measurements.",
            "The query count is Hibernate SQL from the expiration thread only; commit/control messages and concurrent HTTP SQL are excluded.",
            "A faster expiration job may produce empty or very small matched windows. Check n; those windows cannot establish an HTTP latency improvement.",
        ],
    }
    (output / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"per_run": by_run, "pooled_matched_window": pooled}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
