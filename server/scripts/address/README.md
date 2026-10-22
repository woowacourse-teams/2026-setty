# 주소 기준 데이터 만들기

`setty.address` 모듈이 자유 입력 주소를 해석할 때 조회하는 `address_reference` 표(V7)를 채운다. **로컬 DB 전용**이다. dev·prod에 넣는 일은 따로 정한다.

## 원본

원본과 만든 TSV는 레포에 넣지 않는다(`.gitignore`). 레포 밖 폴더에 둔다.

| 파일 | 출처 | 기준일 | 인코딩 | 쓰는 칸 |
|---|---|---|---|---|
| `주소_서울특별시.txt`, `부가정보_서울특별시.txt` | 도로명주소 안내시스템(business.juso.go.kr) 주소DB 전체분 | 2026-09 | cp949 | 관리번호, 도로명코드, 지하, 건물 본번·부번, 우편번호 / 행정동 코드, 건물명 |
| `서울특별시.txt` | 우체국 우편번호 DB(도로명주소, epost.go.kr) | 파일에 없음(2026-10-21 받음) | UTF-8 BOM | 도로명, 시군구, 법정동 이름, 산·지번 |
| `entrc_seoul.txt` (선택) | 도로명주소 안내시스템 위치정보요약DB(출입구) | 2026-09 | cp949 | 출입구 좌표(UTM-K) |

- 두 주소 파일은 관리번호로 잇는다. 2026-09 기준 523,227건 중 36건은 우체국 파일에 없어, 지번을 관리번호(법정동코드 10 + 산 1 + 본번 4 + 부번 4 + 일련번호)에서 꺼낸다. 법정동 코드 1개(주소 3건)는 우체국 파일에 없어 법정동 이름이 빈다.
- 출입구 좌표는 (도로명코드, 지하, 본번, 부번)으로 잇는다. 좌표계는 EPSG:5179(UTM-K, GRS80)이고, 스크립트가 위도·경도(WGS84)로 바꾼다. 서울시청(세종대로 110) 출입구가 알려진 위치에서 20m 안에 들어오는 것으로 확인했다.

## 좌표 이용 조건

위치정보요약DB는 학습 목적으로 받은 자료다. 국외 반출과 좌표 표시가 제한될 수 있으니, **운영(dev·prod)에 넣기 전에 이용 조건을 확인한다.** 그 전까지 좌표는 로컬 실험에서만 쓰고, 좌표 값을 화면·문서·로그에 그대로 내보내지 않는다.

## 다시 만들기

python3만 있으면 된다(외부 라이브러리 없음).

```bash
python3 build_reference.py ~/addressdb/202609_주소DB_전체분 ~/addressdb/서울특별시.txt ~/addressdb/out/address_reference.tsv \
  --with-coords ~/addressdb/entrc_seoul.txt
```

로컬 docker MySQL에 넣는다. 서버의 `local_infile`이 꺼져 있으면 root로 한 번 켠다.

```bash
docker exec dev-mysql mysql -uroot -prootpass -e "SET GLOBAL local_infile = 1"
cd ~/addressdb/out && mysql --local-infile=1 -h127.0.0.1 -usetty -psetty setty < /path/to/server/scripts/address/load_reference.sql
```

- `--with-coords`를 빼면 위도·경도는 NULL로 들어간다. 해석(맞춤·모호·못 맞춤)에는 좌표가 필요 없다.
- 주소DB를 새 기준일로 바꾸면 관리번호가 바뀐 주소가 생길 수 있다. 회원에 저장한 관리번호는 다시 해석해야 맞춰진다.
