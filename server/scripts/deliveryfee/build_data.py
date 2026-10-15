"""배송비 모듈 리소스(행정동 목록·경계·도로 거리표)를 만든다. build_data.sh가 부른다.

사용: python3 build_data.py <행정동 geojson> <주거 건물 geojsonseq> <OSRM 주소> <출력 폴더>
"""
import json
import math
import random
import statistics
import struct
import sys
import urllib.request

DONGS_GEOJSON, BUILDINGS, OSRM, OUT = sys.argv[1:5]
SEOUL = '11'
MIN_BUILDINGS = 10      # 이보다 적으면 대표점을 건물 대신 동 안 격자점으로 잡는다
DIAGONAL_SAMPLES = 5    # 같은 동 거리: 동 안 5곳끼리의 방향 쌍 20개 도로 거리의 중앙값
KX, KY = 111.32 * math.cos(math.radians(37.55)), 110.95   # 서울 위도에서 경도·위도 1도의 km(평면 근사)

# 같은 시드로 같은 표본을 뽑아야 같은 거리표가 나온다
random.seed(20261008)


def polygons(geometry):
    return [geometry['coordinates']] if geometry['type'] == 'Polygon' else geometry['coordinates']


def in_ring(x, y, ring):
    inside, j = False, len(ring) - 1
    for i in range(len(ring)):
        (xi, yi), (xj, yj) = ring[i], ring[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def contains(dong, x, y):
    b = dong['bbox']
    if not (b[0] <= x <= b[2] and b[1] <= y <= b[3]):
        return False
    return any(in_ring(x, y, p[0]) and not any(in_ring(x, y, h) for h in p[1:]) for p in dong['polygons'])


def km(a, b):
    return math.hypot((a[0] - b[0]) * KX, (a[1] - b[1]) * KY)


def load_dongs():
    features = [f for f in json.load(open(DONGS_GEOJSON))['features'] if f['properties']['sido'] == SEOUL]
    dongs = []
    for f in sorted(features, key=lambda f: f['properties']['adm_cd2']):
        ps = polygons(f['geometry'])
        xs = [q[0] for p in ps for q in p[0]]
        ys = [q[1] for p in ps for q in p[0]]
        dongs.append({'code': f['properties']['adm_cd2'], 'name': f['properties']['adm_nm'].split()[-1],
                      'district': f['properties']['sggnm'], 'polygons': ps,
                      'bbox': (min(xs), min(ys), max(xs), max(ys)), 'buildings': []})
    return dongs


def assign_buildings(dongs):
    with open(BUILDINGS) as fh:
        for line in fh:
            line = line.strip().lstrip('\x1e')
            if not line:
                continue
            ring = polygons(json.loads(line)['geometry'])[0][0][:-1]
            x = sum(q[0] for q in ring) / len(ring)
            y = sum(q[1] for q in ring) / len(ring)
            for dong in dongs:
                if contains(dong, x, y):
                    dong['buildings'].append((round(x, 6), round(y, 6)))
                    break


def representative(dong):
    blds = dong['buildings']
    if len(blds) >= MIN_BUILDINGS:
        mx = statistics.median(q[0] for q in blds)
        my = statistics.median(q[1] for q in blds)
        return min(blds, key=lambda q: km(q, (mx, my)))
    b = dong['bbox']
    center = ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
    grid = [(b[0] + (b[2] - b[0]) * (i + .5) / 30, b[1] + (b[3] - b[1]) * (j + .5) / 30) for i in range(30) for j in range(30)]
    return min((q for q in grid if contains(dong, *q)), key=lambda q: km(q, center))


def diagonal_sample(dong):
    blds = dong['buildings']
    if len(blds) >= DIAGONAL_SAMPLES:
        return random.sample(blds, DIAGONAL_SAMPLES)
    b = dong['bbox']
    extra = []
    while len(blds) + len(extra) < DIAGONAL_SAMPLES:
        q = (random.uniform(b[0], b[2]), random.uniform(b[1], b[3]))
        if contains(dong, *q):
            extra.append(q)
    return blds + extra


def table(points):
    coords = ';'.join(f'{x},{y}' for x, y in points)
    response = json.load(urllib.request.urlopen(f'{OSRM}/table/v1/driving/{coords}?annotations=distance'))
    if response['code'] != 'Ok':
        raise SystemExit(response)
    return response['distances']


def main():
    dongs = load_dongs()
    assign_buildings(dongs)
    for dong in dongs:
        dong['rep'] = representative(dong)
        dong['sample'] = diagonal_sample(dong)

    n = len(dongs)
    distances = table([d['rep'] for d in dongs])
    for i, dong in enumerate(dongs):
        inner = table(dong['sample'])
        distances[i][i] = statistics.median(inner[a][b] for a in range(DIAGONAL_SAMPLES) for b in range(DIAGONAL_SAMPLES) if a != b)

    with open(f'{OUT}/seoul-dong-distances.bin', 'wb') as fh:
        fh.write(struct.pack(f'<{n * n}i', *(round(v) for row in distances for v in row)))
    with open(f'{OUT}/seoul-dongs.csv', 'w') as fh:
        for i, dong in enumerate(dongs):
            fh.write(f"{i},{dong['code']},{dong['name']},{dong['district']}\n")
    with open(f'{OUT}/seoul-dong-boundaries.txt', 'w') as fh:
        for i, dong in enumerate(dongs):
            for pi, polygon in enumerate(dong['polygons']):
                for ri, ring in enumerate(polygon):
                    fh.write(f'{i} {pi} {ri} ' + ' '.join(f'{x:.7f},{y:.7f}' for x, y in ring) + '\n')
    fallback = [d['name'] for d in dongs if len(d['buildings']) < MIN_BUILDINGS]
    print(f'행정동 {n}개, 주거 건물 {sum(len(d["buildings"]) for d in dongs)}개, 건물이 적어 격자점을 대표점으로 쓴 동 {len(fallback)}개')


if __name__ == '__main__':
    main()
