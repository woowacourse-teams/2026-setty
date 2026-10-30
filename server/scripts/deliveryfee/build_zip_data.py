"""서울 우편번호 구역 목록과 구역 거리표를 만든다. 로컬 전용이고 python3 표준 라이브러리만 쓴다.

zones: 도로명주소 DB(주소_서울특별시.txt)의 우편번호를 번호순으로 늘어놓아 seoul-zip-zones.csv(index,우편번호)를 만든다.
    python3 build_zip_data.py zones <주소_서울특별시.txt> <seoul-zip-zones.csv>

distances: 우편번호마다 좌표 목록(points.tsv: 우편번호, 경도, 위도, 주거 여부 0/1)을 받아 OSRM으로 거리표를 만든다.
    python3 build_zip_data.py distances <seoul-zip-zones.csv> <points.tsv> <seoul-zip-distances.bin> [--osrm http://localhost:5008]
    좌표 출처는 정해지지 않았으므로 points.tsv와 만든 .bin은 레포에 넣지 않는다.
"""
import argparse
import hashlib
import json
import math
import random
import statistics
import struct
import sys
import time
import urllib.request
from collections import defaultdict

DIAGONAL_SAMPLES = 5
SOURCE_CHUNK = 100
DESTINATION_CHUNK = 2000
LAT0 = 37.55
KX, KY = 111.32 * math.cos(math.radians(LAT0)), 110.95


def zones(address_file, out):
    zips = set()
    with open(address_file, encoding='cp949', errors='replace', newline='') as f:
        for line in f:
            zips.add(line.rstrip('\r\n').split('|')[6])
    ordered = sorted(z for z in zips if len(z) == 5 and z.isdigit())
    with open(out, 'w') as f:
        for i, z in enumerate(ordered):
            f.write(f'{i},{z}\n')
    print(f'우편번호 {len(ordered):,}개 ({ordered[0]} ~ {ordered[-1]})')


def km(a, b):
    return math.hypot((a[0] - b[0]) * KX, (a[1] - b[1]) * KY)


def representative(points):
    mx, my = statistics.median(p[0] for p in points), statistics.median(p[1] for p in points)
    return min(points, key=lambda p: km(p, (mx, my)))


def table(osrm, sources, destinations):
    coords = ';'.join(f'{x:.6f},{y:.6f}' for x, y in sources + destinations)
    query = (f"sources={';'.join(map(str, range(len(sources))))}"
             f"&destinations={';'.join(str(len(sources) + j) for j in range(len(destinations)))}")
    response = json.load(urllib.request.urlopen(f'{osrm}/table/v1/driving/{coords}?{query}&annotations=distance'))
    if response['code'] != 'Ok':
        raise SystemExit(response)
    return response['distances']


# 거리는 10m 단위 uint16. 행 = 구매자 구역, 열 = 판매자 구역(검색이 구매자 한 행만 읽도록).
def distances(zones_file, points_file, out, osrm):
    zips = [line.split(',')[1].strip() for line in open(zones_file) if line.strip()]
    by_zip = defaultdict(list)
    residential = defaultdict(list)
    for line in open(points_file):
        z, lon, lat, res = line.rstrip('\n').split('\t')
        p = (float(lon), float(lat))
        by_zip[z].append(p)
        if res == '1':
            residential[z].append(p)
    # 좌표가 없는 우편번호는 번호가 가장 가까운 구역의 대표점을 빌린다. 우편번호는 구 안에서 이웃끼리 번호가 이어진다.
    known = [i for i, z in enumerate(zips) if z in by_zip]
    if not known:
        raise SystemExit('좌표가 있는 우편번호가 없습니다')
    borrowed = {}
    for i, z in enumerate(zips):
        if z not in by_zip:
            borrowed[z] = zips[min(known, key=lambda k: abs(k - i))]
    if borrowed:
        print(f'좌표가 없어 이웃 구역 대표점을 빌린 우편번호 {len(borrowed)}개: {sorted(borrowed)[:11]}', file=sys.stderr)
    for z, neighbor in borrowed.items():
        by_zip[z] = [representative(residential[neighbor] or by_zip[neighbor])]
    rnd = random.Random(20261023)
    reps = [representative(residential[z] or by_zip[z]) for z in zips]
    n = len(zips)
    started = time.time()

    # OSRM 결과는 [판매자(출발)][구매자(도착)]이므로 뒤집어 담는다
    tens = [[0] * n for _ in range(n)]
    for s in range(0, n, SOURCE_CHUNK):
        sources = reps[s:s + SOURCE_CHUNK]
        for d in range(0, n, DESTINATION_CHUNK):
            rows = table(osrm, sources, reps[d:d + DESTINATION_CHUNK])
            for i, row in enumerate(rows):
                for j, meters in enumerate(row):
                    if meters is None:
                        raise SystemExit(f'경로 없음: {zips[s + i]} → {zips[d + j]}')
                    tens[d + j][s + i] = min(65535, round(meters / 10))
        print(f'  판매자 구역 {min(s + SOURCE_CHUNK, n):,}/{n:,} ({time.time() - started:.0f}초)', file=sys.stderr, flush=True)

    small = 0
    for i, z in enumerate(zips):
        pts = by_zip[z]
        if len(pts) < 2:
            tens[i][i] = 10
            small += 1
            continue
        sample = rnd.sample(pts, DIAGONAL_SAMPLES) if len(pts) >= DIAGONAL_SAMPLES else pts
        inner = table(osrm, sample, sample)
        tens[i][i] = min(65535, round(statistics.median(
            inner[a][b] for a in range(len(sample)) for b in range(len(sample)) if a != b) / 10))

    with open(out, 'wb') as f:
        for row in tens:
            f.write(struct.pack(f'<{n}H', *row))
    digest = hashlib.sha256(open(out, 'rb').read()).hexdigest()
    with open(out + '.sha256', 'w') as f:
        f.write(digest + '\n')
    flat = sorted(v for row in tens for v in row)
    print(f'구역 {n:,}개, {n * n * 2:,}바이트, 걸린 시간 {time.time() - started:.0f}초, 주소 1곳뿐인 구역 {small}개, 대표점을 빌린 구역 {len(borrowed)}개')
    print(f'거리 중앙값 {flat[len(flat) // 2] / 100:.1f}km, 최대 {flat[-1] / 100:.1f}km, sha256 {digest[:12]}')


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='command', required=True)
    z = sub.add_parser('zones')
    z.add_argument('address_file')
    z.add_argument('out')
    d = sub.add_parser('distances')
    d.add_argument('zones_file')
    d.add_argument('points_file')
    d.add_argument('out')
    d.add_argument('--osrm', default='http://localhost:5008')
    args = parser.parse_args()
    if args.command == 'zones':
        zones(args.address_file, args.out)
    else:
        distances(args.zones_file, args.points_file, args.out, args.osrm)


if __name__ == '__main__':
    main()
