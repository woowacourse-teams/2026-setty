"""Summarize per-run maxima (not pseudo-independent rows) and verified HTTP overlap."""
import json
import statistics as st
import sys
from collections import Counter
from itertools import combinations
from pathlib import Path


def dist(values):
    values = list(values)
    if not values:
        return None
    median = st.median(values)
    q = st.quantiles(values, n=4, method='inclusive') if len(values) > 1 else [median] * 3
    return dict(n=len(values), median=median, q25=q[0], q75=q[2], min=min(values), max=max(values))


def main():
    root = Path(sys.argv[1])
    rows = json.loads((root / 'samples.json').read_text())
    meta = json.loads((root / 'metadata.json').read_text())
    assert len(rows) == 2 * 2 * 6 * 12
    metrics = ['job_ms', 'first_release_ms', 'weighted_release_ms']
    for row in rows:
        assert row['transactions'] == (row['due'] + row['size'] - 1) // row['size']
        assert row['sql'] == 1 + 4 * row['transactions']
        assert sum(c['count'] for c in row['chunks']) == row['due']
        for entity in ['order', 'listing']:
            for bound in ['lower', 'upper']:
                name = f'lock_{entity}_{bound}_ms'
                row['max_' + name] = max(c[name] for c in row['chunks'])
                if 'max_' + name not in metrics:
                    metrics.append('max_' + name)
    result = []
    paired = []
    for due in [500, 1000]:
        for with_http in [False, True]:
            for size in meta['sizes']:
                group = [r for r in rows if (r['due'], r['size'], r['with_http']) == (due, size, with_http)]
                assert len(group) == 12
                assert Counter(r['position'] for r in group) == {p: 2 for p in range(1, 7)}
                reqs = [q for r in group for q in r['requests']]
                assert len(reqs) == (24 if with_http else 0)
                result.append(dict(due=due, size=size, with_http=with_http,
                    metrics={metric: dist(r[metric] for r in group) for metric in metrics},
                    sql=group[0]['sql'], transactions=group[0]['transactions'],
                    http_counts=dict(Counter(f"{q['visibility']}:{q['status']}" for q in reqs)),
                    rejected_latency=dist(q['client_ms'] for q in reqs if q['status'] == 400),
                    created_latency=dist(q['client_ms'] for q in reqs if q['status'] == 201),
                    successful_retry_latency=dist(q['retry_ms'] for q in reqs if 'retry_ms' in q)))
            for a, b in combinations(meta['sizes'], 2):
                pairs = [(next(r for r in rows if (r['due'], r['with_http'], r['size'], r['round']) == (due, with_http, a, n)),
                          next(r for r in rows if (r['due'], r['with_http'], r['size'], r['round']) == (due, with_http, b, n)))
                         for n in range(1, 13)]
                paired.append(dict(due=due, with_http=with_http, smaller=a, larger=b,
                    metrics={m: dict(delta_larger_minus_smaller=dist(y[m] - x[m] for x, y in pairs),
                                     larger_is_lower=sum(y[m] < x[m] for x, y in pairs)) for m in metrics}))
    probes = json.loads((root / 'controlled-probes.json').read_text())
    assert len(probes) == 6
    assert all(p['before_commit']['status'] == 400 and p['other_listing']['status'] == 201
               and p['after_commit']['status'] == 201 for p in probes)
    reqs = [q for r in rows for q in r['requests']]
    summary = dict(metadata=meta, groups=result, paired=paired,
                   http_counts=dict(Counter(f"{q['visibility']}:{q['status']}" for q in reqs)),
                   successful_retries=sum(q.get('retry_status') == 201 for q in reqs), controlled_probes_passed=len(probes))
    (root / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    for g in result:
        m = g['metrics']
        print(f"N={g['due']} http={g['with_http']} B={g['size']} job={m['job_ms']['median']:.2f} "
              f"first={m['first_release_ms']['median']:.2f} mean={m['weighted_release_ms']['median']:.2f} "
              f"order={m['max_lock_order_lower_ms']['median']:.2f}..{m['max_lock_order_upper_ms']['median']:.2f} "
              f"listing={m['max_lock_listing_lower_ms']['median']:.2f}..{m['max_lock_listing_upper_ms']['median']:.2f} "
              f"http={g['http_counts']}")
    print('HTTP total', summary['http_counts'], 'retries', summary['successful_retries'])


if __name__ == '__main__':
    main()
