"""주소 채우기 배치 측정용 더미 회원을 만든다. 결과 파일은 레포 밖에 둔다(로컬 전용).

사용: python3 make_members.py <address_reference.tsv> <seoul-dongs.csv> <출력 폴더> [--count 100000] [--seed 20261022]

- members.tsv: members 표에 LOAD DATA로 넣는 행(id, login_id, password, role, phone_number, address, dong_code)
- truth.tsv: 회원마다 만든 방식, 정답 관리번호(서울 밖이면 빈칸), 실제 행정동
"""
import argparse
import random
from pathlib import Path

NULL = r'\N'
PASSWORD = '$2a$10$' + 'x' * 53   # BCrypt 길이(60자)만 맞춘 값. 로그인하지 않는다.
DETAILS = ['101동 1203호', '3층', '201호', 'B동 502호', '1층 상가', '지하1층', '305호']
OUTSIDE_CITIES = ['수원', '성남', '고양', '용인', '부천', '안양']
TYPO_SYLLABLES = '가나다라마바사아자차카타파하강남동서'

# (방식, 비율). 서울 밖 두 방식은 정답이 없다.
KINDS = [
    ('도로명_전체', 20), ('도로명_서울표기', 10), ('도로명_구없음', 10), ('도로명_붙여쓰기', 5),
    ('도로명_상세주소', 20), ('지번', 15), ('오타', 8), ('서울밖_시이름만', 6), ('서울밖_전체', 6),
]


def number(main, sub):
    return main if sub == '0' else f'{main}-{sub}'


def seoul_prefix(rnd):
    return rnd.choice(['서울특별시 ', '서울시 ', '서울 ', ''])


def road(r, underground_space=' '):
    return r['road_name'] + underground_space + ('지하 ' if r['underground'] == '1' else '') + number(r['main'], r['sub'])


def spaced_road_number(name):
    # "논현로12길"을 "논현로 12길"처럼 띄어 쓴다. 길 번호가 없으면 그대로 둔다.
    for i, ch in enumerate(name):
        if ch.isdigit() and i > 0 and name[i - 1] in '로길':
            return name[:i] + ' ' + name[i:]
    return name


def typo(name, rnd):
    positions = [i for i, ch in enumerate(name) if '가' <= ch <= '힣' and ch not in '로길']
    i = rnd.choice(positions) if positions else 0
    replacement = rnd.choice([ch for ch in TYPO_SYLLABLES if ch != name[i]])
    return name[:i] + replacement + name[i + 1:]


def address(kind, r, rnd):
    if kind == '도로명_전체':
        return f"서울특별시 {r['sigungu']} {road(r)}"
    if kind == '도로명_서울표기':
        return f"{rnd.choice(['서울시 ', '서울 ', '서울특별시'])}{r['sigungu']} {road(r)}"
    if kind == '도로명_구없음':
        return f"{seoul_prefix(rnd)}{road(r)}"
    if kind == '도로명_붙여쓰기':
        if rnd.random() < 0.5:
            return f"{r['sigungu']} {road(r, underground_space='')}"
        return f"{r['sigungu']} {spaced_road_number(r['road_name'])} {number(r['main'], r['sub'])}"
    if kind == '도로명_상세주소':
        return f"{seoul_prefix(rnd)}{r['sigungu']} {road(r)}{rnd.choice([', ', ' '])}{rnd.choice(DETAILS)}"
    if kind == '지번':
        sub = '' if r['jibun_sub'] == '0' else f"-{r['jibun_sub']}"
        mountain = '산' if r['jibun_mountain'] == '1' else ''
        return f"{seoul_prefix(rnd)}{r['sigungu']} {r['legal_dong']} {mountain}{r['jibun_main']}{sub}{rnd.choice(['', '번지'])}"
    if kind == '오타':
        return f"서울 {r['sigungu']} {typo(r['road_name'], rnd)} {number(r['main'], r['sub'])}"
    if kind == '서울밖_시이름만':
        return f"{rnd.choice(OUTSIDE_CITIES)} {road(r)}"
    if kind == '서울밖_전체':
        return f"경기도 {rnd.choice(OUTSIDE_CITIES)}시 {road(r)}"
    raise ValueError(kind)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('reference', type=Path)
    parser.add_argument('dongs', type=Path)
    parser.add_argument('out', type=Path)
    parser.add_argument('--count', type=int, default=100_000)
    parser.add_argument('--seed', type=int, default=20261022)
    args = parser.parse_args()
    rnd = random.Random(args.seed)

    names = ['mgmt_no', 'road_code', 'road_name', 'underground', 'main', 'sub', 'sigungu', 'legal_dong',
             'jibun_mountain', 'jibun_main', 'jibun_sub', 'zip_code', 'dong_code']
    with open(args.reference, encoding='utf-8') as f:
        rows = [dict(zip(names, line.rstrip('\n').split('\t'))) for line in f]
    rows = [r for r in rows if r['dong_code'] != NULL and r['legal_dong'] != NULL]
    dongs = [line.split(',')[1] for line in args.dongs.read_text(encoding='utf-8').splitlines() if line.strip()]
    kinds = [k for k, _ in KINDS]
    weights = [w for _, w in KINDS]

    args.out.mkdir(parents=True, exist_ok=True)
    with open(args.out / 'members.tsv', 'w', encoding='utf-8', newline='\n') as members, \
            open(args.out / 'truth.tsv', 'w', encoding='utf-8', newline='\n') as truth:
        for member_id in range(1, args.count + 1):
            r = rnd.choice(rows)
            kind = rnd.choices(kinds, weights)[0]
            # 고른 동: 60% 실제 동, 10% 다른 동(잘못 고름), 30% 비움(행정동 선택 전 가입자)
            p = rnd.random()
            chosen = r['dong_code'] if p < 0.6 else rnd.choice(dongs) if p < 0.7 else NULL
            members.write('\t'.join([str(member_id), f'bf{member_id}', PASSWORD, 'MEMBER', '010-0000-0000',
                                     address(kind, r, rnd), chosen]) + '\n')
            expected = '' if kind.startswith('서울밖') else r['mgmt_no']
            truth.write('\t'.join([str(member_id), kind, expected, r['dong_code']]) + '\n')


if __name__ == '__main__':
    main()
