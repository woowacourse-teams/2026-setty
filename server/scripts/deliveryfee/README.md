# 배송비 모듈 리소스 만들기

`setty.deliveryfee` 모듈이 시작할 때 읽는 리소스(`src/main/resources/deliveryfee`)를 다시 만든다. 행정동 경계가 개편되거나 지도(OSM)를 갱신할 때 쓴다.

## 만드는 파일

| 파일 | 크기 | 내용 |
|---|---|---|
| `seoul-dong-distances.bin` | 729,316B | 427×427 도로 거리(m), int32 리틀엔디언. 행 = 판매자 동, 열 = 구매자 동 |
| `seoul-dongs.csv` | 15,351B | index, 행정동 코드(10자리), 동 이름, 구 |
| `seoul-dong-boundaries.txt` | 436,097B | 한 줄에 고리 하나: 동 index, 폴리곤 번호, 고리 번호(0 바깥, 나머지 구멍), 경도,위도 … |

크기는 2026-07-01 경계 기준이다. 세 파일의 동 순서(index)는 행정동 코드순으로 같다.

## 현재 리소스의 출처

- 행정동 경계: [vuski/admdongkor](https://github.com/vuski/admdongkor) `HangJeongDong_ver20260701.geojson`(2026-07-01 기준), 서울 427개 동
- 도로 거리: OSRM(MLD, car 프로필), OSM geofabrik south-korea 2026-10-06 스냅샷
- 동 대표점: 동 안 OSM 주거 건물 중심점의 중앙값에 가장 가까운 건물. 건물이 10개 미만인 동은 동 안 격자점
- 같은 동 거리: 동 안 5곳끼리의 방향 쌍 20개 도로 거리의 중앙값

## 실행

Docker와 python3가 필요하다. 작업 폴더에 원본 약 400MB(전국 지도 약 275MB, 행정동 경계 약 35MB, 중간 파일)를 내려받으니 레포 밖 폴더를 쓴다.

```bash
WORK=~/deliveryfee-work ./build_data.sh
```

- 행정동 경계를 바꾸려면 `DONGS_URL`을 새 버전 주소로 준다.
- geofabrik의 latest 파일은 매일 바뀐다. 같은 결과가 필요하면 `OSM_URL`에 날짜가 붙은 파일 주소를 준다.
- 대표점 표본은 시드를 고정해서, 같은 입력이면 같은 파일이 나온다.
- 경계를 바꾸면 동 index가 바뀌므로, 저장해 둔 동 index(예: 매물의 동 번호)도 다시 계산해야 한다.

## 우편번호 구역 (zone-mode=zip)

`build_zip_data.py`로 만든다. python3 표준 라이브러리만 쓴다.

| 파일 | 크기 | 레포 | 내용 |
|---|---|---|---|
| `seoul-zip-zones.csv` | 60,864B | 넣음 | index, 우편번호. 2026-09 도로명주소 DB의 서울 우편번호 5,634개를 번호순으로. 구 안에서 이웃 구역끼리 번호가 이어져 검색 조건을 범위로 묶을 수 있다 |
| `seoul-zip-distances.bin` | 63,483,912B | **넣지 않음** | 5,634×5,634 도로 거리, 10m 단위 uint16 리틀엔디언. **행 = 구매자 구역, 열 = 판매자 구역**(동 거리표와 반대. 검색이 구매자 한 행만 읽도록) |

```bash
python3 build_zip_data.py zones <주소DB 폴더>/주소_서울특별시.txt ../../src/main/resources/deliveryfee/seoul-zip-zones.csv
python3 build_zip_data.py distances ../../src/main/resources/deliveryfee/seoul-zip-zones.csv <points.tsv> <작업 폴더>/seoul-zip-distances.bin
```

- `points.tsv`는 우편번호, 경도, 위도, 주거 여부(0/1)를 탭으로 나눈 주소 좌표 목록이다. 대표점은 구역 안 주거 주소의 중앙값에 가장 가까운 주소(주거 주소가 없으면 전체 주소), 같은 구역 거리는 구역 안 5곳의 방향 쌍 20개 도로 거리의 중앙값이다.
- 좌표가 없는 우편번호는 번호가 가장 가까운 구역의 대표점을 빌린다. 스크립트가 개수를 출력한다.
- **좌표 출처는 정해지지 않았다.** 출처가 정해지기 전에는 `points.tsv`와 `.bin`을 레포·서버에 올리지 않는다.
- OSRM은 표 하나에 출발 100 × 도착 2,000곳을 묻는다. `osrm-routed --max-table-size 2100` 이상으로 띄운다.
- 운영 배포는 S3에 둔다(jar에 넣지 않음). `DELIVERY_FEE_ZONE_MODE=zip`일 때만 앱이 시작하면서 다음을 한다. 기본값(dong)이면 S3·파일·폴더를 건드리지 않는다.
  1. `DELIVERY_FEE_ZIP_TABLE_DIR`(기본 `/opt/setty/data`)에 `seoul-zip-distances-<sha256 앞 12자>.bin`이 있고 해시가 `DELIVERY_FEE_ZIP_TABLE_SHA256`과 같으면 그 파일을 메모리 매핑한다.
  2. 없으면 `DELIVERY_FEE_ZIP_TABLE_BUCKET`(기본 `SETTY_S3_BUCKET`)의 `DELIVERY_FEE_ZIP_TABLE_KEY`를 같은 폴더의 임시 파일로 받고, 해시를 확인한 뒤 같은 이름으로 원자적으로 옮긴다. 두 슬롯이 동시에 받아도 덜 받은 파일을 매핑하지 않는다.
  3. 받기·해시 확인·크기 확인에 실패하거나 `DELIVERY_FEE_ZIP_TABLE_TIMEOUT`(기본 30초) 안에 받지 못하면 경고 로그를 남기고 행정동으로 계산한다. 기동은 막지 않는다.
- 해시는 `build_zip_data.py distances`가 만드는 `.sha256` 파일 값이다. 무중단 배포 때 두 슬롯이 같은 파일을 매핑하면 운영체제 페이지 캐시 한 벌을 같이 쓴다.
- 로컬에서 켜 볼 때는 폴더에 해시 이름으로 파일을 두면 S3 없이 쓴다.

```bash
DELIVERY_FEE_ZONE_MODE=zip DELIVERY_FEE_ZIP_TABLE_DIR=<폴더> DELIVERY_FEE_ZIP_TABLE_SHA256=<해시> ./gradlew bootRun
```

- 우편번호 목록이 바뀌면 구역 번호가 밀리므로, 매물에 저장한 `zip_zone`을 다시 채워야 한다.

### 켜는 순서

1. 회원 주소 채우기: 주소 기준 데이터를 넣고 `SETTY_ADDRESS_BACKFILL_ENABLED=true`로 회원 표준 주소(우편번호)를 채운다.
2. 매물 구역 채우기: `SETTY_LISTING_ZIP_ZONE_BACKFILL_ENABLED=true`로 기존 매물의 `zip_zone`을 판매자 우편번호로 채운다. 1이 끝난 뒤에 켜야 판매자 우편번호가 있다.
3. `DELIVERY_FEE_ZONE_MODE=zip`(거리표 S3 키·해시 설정과 함께).

순서를 거꾸로 하면 우편번호가 있는 구매자의 배송비 필터에서 `zip_zone`이 빈 기존 매물이 빠진다. 우편번호 필터는 구역이 있는 매물만 거르기 때문이다(행정동 필터가 행정동 없는 매물을 빼는 것과 같은 규칙).
