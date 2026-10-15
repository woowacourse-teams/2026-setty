"""Compare balanced, paired chunk-size runs without treating rows as independent users."""
import json
import statistics
import sys
from itertools import combinations
from pathlib import Path


def distribution(values):
    quartiles = statistics.quantiles(values, n=4, method="inclusive")
    median = statistics.median(values)
    return {
        "n": len(values),
        "median_ms": median,
        "q25_ms": quartiles[0],
        "q75_ms": quartiles[2],
        "min_ms": min(values),
        "max_ms": max(values),
        "median_absolute_deviation_ms": statistics.median(abs(x - median) for x in values),
    }


def main():
    output = Path(sys.argv[1])
    samples = json.loads((output / "samples.json").read_text())
    metadata = json.loads((output / "metadata.json").read_text())
    candidates = metadata["batch_sizes"]
    repetitions = metadata["measured_runs_per_size"]
    assert len(samples) == repetitions * len(candidates)
    metrics = ("job_ms", "first_release_ms", "listing_weighted_mean_release_ms")
    sizes = {}
    for size in candidates:
        rows = [row for row in samples if row["batch_size"] == size]
        assert len(rows) == repetitions
        assert sorted(row["round"] for row in rows) == list(range(1, repetitions + 1))
        assert all(sum(row["position"] == p for row in rows) == repetitions // len(candidates)
                   for p in range(1, len(candidates) + 1))
        sizes[size] = {metric: distribution([row[metric] for row in rows]) for metric in metrics}
        sizes[size]["max_chunk_transaction_per_run"] = distribution([
            max(chunk["transaction_ms"] for chunk in row["chunks"]) for row in rows
        ])
        sizes[size]["sql_counts"] = sorted({row["hibernate_sql"] for row in rows})
        sizes[size]["transaction_counts"] = sorted({row["hibernate_transactions"] for row in rows})
    paired = []
    for smaller, larger in combinations(candidates, 2):
        pairs = [
            (next(r for r in samples if r["round"] == round_id and r["batch_size"] == smaller),
             next(r for r in samples if r["round"] == round_id and r["batch_size"] == larger))
            for round_id in range(1, repetitions + 1)
        ]
        paired.append({
            "smaller": smaller,
            "larger": larger,
            "metrics": {
                metric: {
                    "larger_minus_smaller_ms": distribution([b[metric] - a[metric] for a, b in pairs]),
                    "larger_faster_rounds": sum(b[metric] < a[metric] for a, b in pairs),
                } for metric in metrics
            },
        })
    summary = {
        "metadata": metadata,
        "by_batch_size": sizes,
        "paired_comparisons": paired,
        "limits": [
            "One local JVM and database; 12 balanced repetitions per size are not 12 independent production environments.",
            "Transaction method time bounds lock exposure but is not a direct measurement of each row's lock duration.",
            "Release offsets end at service return after commit; they measure database availability, not user response time.",
            "No background HTTP or same-row contention is applied. Failure fallback is covered by separate regression tests.",
        ],
    }
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"by_batch_size": sizes, "paired_comparisons": paired}, indent=2))


if __name__ == "__main__":
    main()
