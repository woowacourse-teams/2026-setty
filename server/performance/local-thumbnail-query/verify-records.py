"""Recheck selected historical records without contacting a server or changing files."""
import argparse
import csv
import hashlib
import json
import math
import statistics
from collections import Counter
from datetime import datetime
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text())


def rows(path):
    with path.open(newline='') as stream:
        return list(csv.DictReader(stream))


def stats(values):
    values = sorted(values)
    require(bool(values), 'Empty measurement group')
    require(all(math.isfinite(v) and v >= 0 for v in values), 'Invalid duration')
    return {'min': min(values), 'median': statistics.median(values),
            'p95': values[math.ceil(len(values) * .95) - 1],
            'max': max(values), 'mean': statistics.mean(values)}


def close(actual, expected, label):
    require(math.isclose(actual, expected, rel_tol=1e-12, abs_tol=1e-9),
            f'{label}: {actual} != {expected}')


def verify_local(root):
    data = rows(root / 'samples.csv')
    saved = read_json(root / 'comparison.json')
    require(saved['verified'] and len(data) == saved['http_requests'] == 1284, 'Local completion')
    require(len({r['requestId'] for r in data}) == saved['unique_request_ids'] == 1284,
            'Local request IDs')
    counts = Counter((r['photos'], r['variant'], r['phase']) for r in data)
    expected = {(str(p), v, phase): count for p in (1, 5) for v in ('min-order', 'order-one')
                for phase, count in [('preflight', 1), ('warmup', 20), ('measurement', 300)]}
    require(counts == expected, 'Local condition matrix')
    for row in data:
        require(row['status'] == '200' and bool(row['requestId']), 'Local response')
        require(all(row[k] == '5020' for k in ('listing_count', 'image_count', 'summary_count')),
                'Local item counts')
        require(row['body_sha256'] == saved['body_sha256'], 'Local response hash')
        require(int(row['response_bytes']) == saved['response_bytes'], 'Local response bytes')
    measured = [r for r in data if r['phase'] == 'measurement']
    require(len(measured) == saved['measured_requests'] == 1200, 'Local measurement count')
    require(len(saved['comparison']) == 2 and len(saved['per_run']) == 12, 'Local summary groups')
    for comparison in saved['comparison']:
        photo = str(comparison['photos'])
        for variant, metrics in comparison['variants'].items():
            group = [r for r in measured if r['photos'] == photo and r['variant'] == variant]
            for metric, values in metrics.items():
                actual = stats([float(r[metric]) for r in group])
                for key, value in values.items():
                    close(actual[key], value, f'local {photo} {variant} {metric} {key}')
        for metric, value in comparison['median_reduction_percent'].items():
            before = comparison['variants']['min-order'][metric]['median']
            after = comparison['variants']['order-one'][metric]['median']
            close(100 * (1 - after / before), value, 'Local reduction')
    for summary in saved['per_run']:
        group = [r for r in measured if int(r['photos']) == summary['photos']
                 and int(r['run']) == summary['run'] and r['variant'] == summary['variant']]
        require(len(group) == summary['count'] == 100, 'Local run size')
        for metric in ('images_ms', 'filter_ms', 'client_ms'):
            actual = stats([float(r[metric]) for r in group])
            for key in ('median', 'p95', 'max'):
                close(actual[key], summary[metric + '_' + key], 'Local run statistic')
    return len(data)


def verify_dev(root):
    saved = read_json(root / 'comparison.json')
    evidence = read_json(root / 'verification-evidence.json')
    initial = evidence['initial-dev-snapshot.json']
    api = evidence['initial-api-snapshot.json']
    require(saved['complete'] and saved['original_data_restored'], 'Dev completion')
    require(saved['exported_log_rows'] == 0, 'Dev raw server rows are not published')
    require(initial['available'] == api['item_count'] == saved['available_listings'] == 5138,
            'Dev initial count')
    expected = [(f'photos-{p}', n) for p in (1, 5) for n in (1, 2, 3)] + [('restored', 1)]
    require([(r['condition'], r['run']) for r in saved['runs']] == expected, 'Dev run matrix')
    all_ids, previous_post, photo_hashes = [], None, {}
    for run in saved['runs']:
        sub = f"{run['condition']}/run-{run['run']}/"
        data = rows(root / sub / 'samples.csv')
        ids = [r['request_id'] for r in data]
        all_ids.extend(ids)
        require(len(ids) == len(set(ids)) == 120 and all(ids), 'Dev per-run IDs')
        journal, client = run['journal_summary'], run['client_summary']
        require(client['passed'] and client['gradle_exit_code'] == 0, 'Dev client success')
        require(client['failed'] == client['missing_ids'] == 0, 'Dev client failures')
        require(client['requests'] == journal['requests'] == journal['unique_ids']
                == journal['valid'] == 120 and journal['passed'], 'Dev journal validity')
        require(journal['measurement'] == client['measurement'] == 100 and client['warmup'] == 20,
                'Dev summary phase counts')
        require(hashlib.sha256('\n'.join(ids).encode()).hexdigest() == journal['ids_ordered_sha256'],
                'Dev journal ordered ID hash')
        require(run['server_ms'] == journal['measurement_ms'], 'Dev server summary copy')
        require(Counter(r['phase'] for r in data) ==
                {'listings-warmup': 20, 'listings-measurement': 100}, 'Dev phases')
        for row in data:
            require(row['expected_items'] == '5138' and row['failed'] == 'false', 'Dev response')
            require(0 <= int(row['response_time_ms']) < 5000, 'Dev stop threshold')
            require(int(row['response_body_bytes']) == api['body_bytes'], 'Dev body bytes')
        actual = stats([float(r['response_time_ms']) for r in data if r['phase'] == 'listings-measurement'])
        require(run['client_ms'] == client['measurement_ms'], 'Dev client summary copy')
        for key, value in run['client_ms'].items():
            close(actual['p95' if key == 'p95_nearest_rank' else key], value, 'Dev client statistic')
        for key in ('pre_api', 'post_api'):
            probe = client[key]
            require(probe['status'] == 200 and probe['item_count'] == 5138, 'Dev API probe')
            require(probe['body_sha256'] == api['body_sha256']
                    and probe['body_bytes'] == api['body_bytes'], 'Dev API body')
        if previous_post:
            require((datetime.fromisoformat(client['pre_api']['at_utc']) - previous_post).total_seconds()
                    >= 60, 'Dev spacing')
        previous_post = datetime.fromisoformat(client['post_api']['at_utc'])
        snap = evidence[sub + 'server-post.json']
        for key in ('jar_sha256', 'service', 'listings_sha256'):
            require(snap[key] == initial[key], f'Dev unchanged {key}')
        require(snap['available'] == 5138 and snap['fixture'] == 5000
                and snap['other_available_images'] == 138, 'Dev fixture counts')
        require(snap['order_one_query'] and not snap['min_subquery'], 'Dev candidate query')
        photo = 5 if run['condition'] == 'photos-5' else 1
        require(snap['fixture_images'] == 5000 * photo, 'Dev photo count')
        photo_hashes.setdefault(photo, snap['listing_images_sha256'])
        require(snap['listing_images_sha256'] == photo_hashes[photo], 'Dev photo stability')
        if photo == 1:
            require(snap['listing_images_sha256'] == initial['listing_images_sha256']
                    and snap['fixture_one_image_each'] == 5000, 'Dev photo baseline')
    require(len(all_ids) == len(set(all_ids)) == saved['matched_requests'] == 840, 'Dev total IDs')
    require(saved['measurement_requests'] == 700, 'Dev measured requests')
    for name, before, after, commit, added, removed in (
            ('rollback-trial.json', 1, 5, False, 20000, 0),
            ('transition-to-five.json', 1, 5, True, 20000, 0),
            ('restoration-trial.json', 5, 1, False, 0, 20000),
            ('restoration-commit.json', 5, 1, True, 0, 20000)):
        event = evidence[name]
        require((event['before'], event['after'], event['commit'], event['returncode']) ==
                (before, after, commit, 0), 'Dev transition result')
        if 'stdout' in event:
            require(not event['stderr'] and
                    f'added_images\tremoved_images\n{added}\t{removed}\n' in event['stdout']
                    and 'available_listings_after_run\n5138\n' in event['stdout'], 'Dev SQL result')
        else:
            require((event['added_images'], event['removed_images']) == (added, removed), 'Dev SQL changes')
            require(event['fixture_images_after_run'] == 5000 * (after if commit else before),
                    'Dev transaction photo count')
    for name, photo in [('rollback-post-snapshot.json', 1), ('restoration-after-rollback.json', 5),
                        ('restored-pre.json', 1), ('final-dev-snapshot.json', 1)]:
        snap = evidence[name]
        require(snap['listing_images_sha256'] == photo_hashes[photo]
                and snap['fixture_images'] == 5000 * photo, 'Dev restored photo rows')
        require(snap['listings_sha256'] == initial['listings_sha256']
                and snap['available'] == 5138 and snap['other_available_images'] == 138,
                'Dev preserved other rows')
        require(snap['jar_sha256'] == initial['jar_sha256'] and snap['service'] == initial['service'],
                'Dev stable deployment')
    return len(all_ids)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--records', type=Path, default=Path(__file__).resolve().parent / 'records')
    root = parser.parse_args().records
    for path, source in read_json(root / 'source-manifest.json').items():
        require(hashlib.sha256((root / path).read_bytes()).hexdigest() == source['sha256'],
                f'Preserved source changed: {path}')
    local_count = verify_local(root / 'local-20261011')
    dev_count = verify_dev(root / 'dev-20261012')
    attempt = read_json(root / 'min-attempt-20261011.json')
    require(attempt['validation_passed'] and not attempt['experiment_completed']
            and attempt['after_failed'] == 1, 'MIN attempt must remain incomplete')
    print(json.dumps({'verified': True, 'local_requests': local_count, 'dev_requests': dev_count,
                      'dev_server_statistics_recomputed': False,
                      'scope': 'Local statistics and dev client statistics recalculated; dev server summaries, ID hashes and recorded restoration checked.'},
                     indent=2))


if __name__ == '__main__':
    main()
