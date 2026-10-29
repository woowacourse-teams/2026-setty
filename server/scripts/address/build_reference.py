"""주소 기준 데이터(address_reference 표에 넣을 TSV)를 만든다. 원본과 결과 파일은 레포 밖에 둔다.

사용: python3 build_reference.py <주소DB 폴더> <우편번호 파일> <출력 TSV> [--with-coords <위치정보요약DB 파일>]
"""
import argparse
import math
import sys
from pathlib import Path

NULL = r'\N'


def read_pipe(path, encoding):
    with open(path, encoding=encoding, newline='') as f:
        for line in f:
            yield line.rstrip('\r\n').split('|')


def utmk_to_wgs84(x, y):
    # EPSG:5179(UTM-K, GRS80 횡메르카토르: 원점 위도 38, 경도 127.5, 축척 0.9996, 가산 1,000,000 / 2,000,000) 역변환
    a, f = 6378137.0, 1 / 298.257222101
    e2 = f * (2 - f)
    ep2 = e2 / (1 - e2)
    k0 = 0.9996
    lat0, lon0 = math.radians(38), math.radians(127.5)
    n0 = 1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256

    def meridian(p):
        return a * (n0 * p - (3 * e2 / 8 + 3 * e2 ** 2 / 32 + 45 * e2 ** 3 / 1024) * math.sin(2 * p)
                    + (15 * e2 ** 2 / 256 + 45 * e2 ** 3 / 1024) * math.sin(4 * p)
                    - (35 * e2 ** 3 / 3072) * math.sin(6 * p))

    mu = (meridian(lat0) + (y - 2000000) / k0) / (a * n0)
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    p1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu)
          + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
          + (151 * e1 ** 3 / 96) * math.sin(6 * mu) + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    c1 = ep2 * math.cos(p1) ** 2
    t1 = math.tan(p1) ** 2
    n1 = a / math.sqrt(1 - e2 * math.sin(p1) ** 2)
    r1 = a * (1 - e2) / (1 - e2 * math.sin(p1) ** 2) ** 1.5
    d = (x - 1000000) / (n1 * k0)
    lat = p1 - (n1 * math.tan(p1) / r1) * (
            d ** 2 / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * ep2) * d ** 4 / 24
            + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * ep2 - 3 * c1 ** 2) * d ** 6 / 720)
    lon = lon0 + (d - (1 + 2 * t1 + c1) * d ** 3 / 6
                  + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * ep2 + 24 * t1 ** 2) * d ** 5 / 120) / math.cos(p1)
    return math.degrees(lat), math.degrees(lon)


def load_coords(path):
    # 키(도로명코드, 지하, 본번, 부번)마다 출입구 한 줄이다. 좌표가 빈 줄은 건너뛴다.
    coords = {}
    for p in read_pipe(path, 'cp949'):
        if p[16] and p[17]:
            coords[(p[6], p[8], p[9], p[10])] = utmk_to_wgs84(float(p[16]), float(p[17]))
    return coords


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('address_dir', type=Path, help='주소_서울특별시.txt, 부가정보_서울특별시.txt가 있는 폴더(cp949)')
    parser.add_argument('zip_file', type=Path, help='우체국 우편번호 DB 서울특별시.txt(UTF-8 BOM)')
    parser.add_argument('out', type=Path)
    parser.add_argument('--with-coords', type=Path, metavar='ENTRANCE_FILE',
                        help='위치정보요약DB(entrc_seoul.txt, cp949). 로컬 실험 전용')
    args = parser.parse_args()

    extra = {p[0]: p for p in read_pipe(args.address_dir / '부가정보_서울특별시.txt', 'cp949')}

    # 우체국 파일은 도로명·법정동 이름과 지번을 준다. 관리번호가 없는 주소는 코드별 이름으로 채운다.
    # 2026-09 기준 법정동 코드 1개(주소 3건)는 우체국 파일에 없어 법정동 이름이 빈다.
    by_mgmt, road_names, dong_names, sigungu_names = {}, {}, {}, {}
    rows = read_pipe(args.zip_file, 'utf-8-sig')
    header = {name: i for i, name in enumerate(next(rows))}
    for p in rows:
        z = {name: p[i] for name, i in header.items()}
        by_mgmt[z['건물관리번호']] = z
        road_names[z['도로명코드']] = z['도로명']
        dong_names[z['법정동코드']] = z['법정동명']
        sigungu_names[z['법정동코드'][:5]] = z['시군구']

    coords = load_coords(args.with_coords) if args.with_coords else {}

    written = no_zip_row = with_coords = 0
    with open(args.out, 'w', encoding='utf-8', newline='\n') as out:
        for p in read_pipe(args.address_dir / '주소_서울특별시.txt', 'cp949'):
            mgmt, road_code, underground, main_no, sub_no, zip_code = p[0], p[1], p[3], p[4], p[5], p[6]
            z = by_mgmt.get(mgmt)
            if z:
                mountain, jibun_main, jibun_sub = z['산여부'], z['지번본번'], z['지번부번'] or '0'
            else:
                # 관리번호 = 법정동코드(10) + 산 여부(1) + 지번 본번(4) + 부번(4) + 일련번호
                no_zip_row += 1
                mountain, jibun_main, jibun_sub = mgmt[10], str(int(mgmt[11:15])), str(int(mgmt[15:19]))
            e = extra.get(mgmt)
            dong_code = e[1] if e and e[1] else NULL
            building_name = (e[7] or e[6]) if e and (e[7] or e[6]) else NULL
            lat_lon = coords.get((road_code, underground, main_no, sub_no))
            if lat_lon:
                with_coords += 1
            out.write('\t'.join([
                mgmt, road_code, road_names[road_code], underground, main_no, sub_no,
                sigungu_names[mgmt[:5]], dong_names.get(mgmt[:10], NULL), mountain, jibun_main, jibun_sub,
                zip_code, dong_code, building_name,
                f'{lat_lon[0]:.6f}' if lat_lon else NULL, f'{lat_lon[1]:.6f}' if lat_lon else NULL,
            ]) + '\n')
            written += 1

    print(f'주소 {written:,}건, 우체국 파일에 없어 관리번호로 지번을 채운 주소 {no_zip_row}건'
          + (f', 좌표 붙음 {with_coords:,}건' if args.with_coords else ''), file=sys.stderr)


if __name__ == '__main__':
    main()
