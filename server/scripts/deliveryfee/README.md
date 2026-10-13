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
