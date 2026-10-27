"""Summarize opt-in local MySQL plans; never treat timings as regression thresholds."""
import argparse
import csv
import json
from pathlib import Path
import re
import statistics

parser = argparse.ArgumentParser()
parser.add_argument('directory', type=Path)
args = parser.parse_args()
source = json.loads((args.directory / 'samples.json').read_text())
groups = {}
rows = []
pattern = r'actual time=([\d.eE+-]+)\.\.([\d.eE+-]+) rows=(\d+) loops=(\d+)'
for sample in source:
    plan = sample['plan'].splitlines()
    root = re.search(pattern, plan[0])
    access = re.search(pattern, plan[-1])
    assert root and access and int(root[4]) == 1 and int(access[4]) == 1, sample
    assert int(root[3]) == (500 if sample['query'] == 'requests' else 50), sample
    row = {key: sample[key] for key in ['scenario', 'phase', 'run', 'mode', 'query']}
    row.update(root_ms=float(root[2]), returned_rows=int(root[3]), access_rows=int(access[3]),
               has_sort=any('-> Sort:' in line for line in plan), access=plan[-1].strip())
    rows.append(row)
    groups.setdefault((sample['scenario'], sample['query'], sample['mode']), []).append(row)

assert len(source) == 128
summary = []
for key in sorted(groups):
    group = groups[key]
    warmup = [row for row in group if row['phase'] == 'warmup']
    measurement = [row for row in group if row['phase'] == 'measurement']
    assert len(warmup) == 2 and len(measurement) == 6
    assert {row['run'] for row in measurement} == set(range(1, 7))
    values = [row['root_ms'] for row in measurement]
    entry = dict(zip(['scenario', 'query', 'mode'], key))
    entry.update(n=len(values), median_ms=round(statistics.median(values), 6),
                 min_ms=min(values), max_ms=max(values), samples_ms=values,
                 access_rows=sorted({row['access_rows'] for row in measurement}),
                 sort_present=sorted({row['has_sort'] for row in measurement}),
                 example_access=measurement[0]['access'])
    summary.append(entry)

report = {'warmup_executions': 32, 'measurement_executions': 96,
          'measurement': 'EXPLAIN ANALYZE root iterator end time, ms, loops=1', 'summary': summary}
(args.directory / 'comparison.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
with (args.directory / 'samples.csv').open('w', newline='') as stream:
    writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
for entry in summary:
    print(f"{entry['scenario']:18} {entry['query']:10} {entry['mode']:14} "
          f"median={entry['median_ms']:.6f}ms access_rows={entry['access_rows']} sort={entry['sort_present']}")
