"""Verify preserved request evidence and summarize this local comparison."""
import argparse
import csv
import hashlib
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("result_directory", type=Path)
root = parser.parse_args().result_directory
samples = [json.loads(line) for line in (root / "samples.jsonl").read_text().splitlines()]
summary = json.loads((root / "summary.json").read_text())
assert summary["completed"] is True
assert len(samples) == summary["total_requests"] == 1284
ids = [sample["requestId"] for sample in samples]
assert len(set(ids)) == len(ids)
body_hash = hashlib.sha256((root / "response-baseline.json").read_bytes()).hexdigest()
assert body_hash == summary["body_sha256"]
assert len(json.loads((root / "response-baseline.json").read_text())["items"]) == 5020

raw = {}
for line in (root / "run.log").read_text().splitlines():
    line = line.strip()
    if not line.startswith('{"@timestamp"'):
        continue
    event = json.loads(line)
    if event.get("event") == "listing_request_timing":
        assert event["requestId"] not in raw
        raw[event["requestId"]] = event
assert set(raw) == set(ids)

metrics = ("images_ms", "filter_ms", "client_ms")
for sample in samples:
    assert sample["body_sha256"] == body_hash
    assert sample["status"] == 200 and sample["outcome"] == "completed"
    for key in ("response_complete", "listings_completed", "images_completed", "mapping_completed"):
        assert sample[key] is True
    for key in ("listing_count", "image_count", "summary_count"):
        assert sample[key] == 5020
    for key in ("status", "filter_ms", "listings_ms", "images_ms", "mapping_ms",
                "listing_count", "image_count", "summary_count", "response_complete", "outcome"):
        assert sample[key] == raw[sample["requestId"]][key]

csv_rows = list(csv.DictReader((root / "samples.csv").open()))
assert len(csv_rows) == len(samples)
assert [row["requestId"] for row in csv_rows] == ids
for row, sample in zip(csv_rows, samples):
    for metric in metrics:
        assert float(row[metric]) == sample[metric]

phase_counts = Counter((sample["photos"], sample["variant"], sample["phase"]) for sample in samples)
for photos in (1, 5):
    for variant in ("min-order", "order-one"):
        assert phase_counts[photos, variant, "preflight"] == 1
        assert phase_counts[photos, variant, "warmup"] == 20
        assert phase_counts[photos, variant, "measurement"] == 300

groups = defaultdict(list)
pairs = defaultdict(dict)
for sample in samples:
    if sample["phase"] == "measurement":
        groups[sample["photos"], sample["run"], sample["variant"]].append(sample)
        pairs[sample["photos"], sample["run"], sample["pair"]][sample["variant"]] = sample
assert len(pairs) == 600
assert all(set(pair) == {"min-order", "order-one"} for pair in pairs.values())

def describe(values):
    values = sorted(values)
    return {"median": statistics.median(values), "p95": values[math.ceil(len(values) * .95) - 1],
            "max": max(values)}

for row in summary["rows"]:
    group = groups[row["photos"], row["run"], row["variant"]]
    assert len(group) == row["count"] == 100
    for metric in metrics:
        for key, value in describe([sample[metric] for sample in group]).items():
            assert row[metric + "_" + key] == value

aggregates = []
for photos in (1, 5):
    comparison = {"photos": photos, "variants": {}}
    for variant in ("min-order", "order-one"):
        group = [s for s in samples if s["phase"] == "measurement"
                 and s["photos"] == photos and s["variant"] == variant]
        comparison["variants"][variant] = {metric: describe([s[metric] for s in group]) for metric in metrics}
    before, after = (comparison["variants"][v] for v in ("min-order", "order-one"))
    comparison["median_reduction_percent"] = {
        metric: 100 * (1 - after[metric]["median"] / before[metric]["median"]) for metric in metrics}
    selected_pairs = [p for key, p in pairs.items() if key[0] == photos]
    differences = [p["min-order"]["images_ms"] - p["order-one"]["images_ms"] for p in selected_pairs]
    comparison["paired_images_ms_saved_median"] = statistics.median(differences)
    comparison["pairs_candidate_faster"] = sum(delta > 0 for delta in differences)
    aggregates.append(comparison)

result = {"verified": True, "http_requests": len(samples), "measured_requests": 1200,
          "unique_request_ids": len(set(ids)), "body_sha256": body_hash,
          "response_bytes": len((root / "response-baseline.json").read_bytes()),
          "comparison": aggregates, "per_run": summary["rows"]}
(root / "comparison.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
