# 주소 기준 데이터 만들기

`setty.address` 모듈이 자유 입력 주소를 해석할 때 조회하는 `address_reference` 표(V7)를 채운다. 아래 스크립트는 로컬 DB용이고, dev·prod는 [앱이 S3에서 받아 넣는다](#devprod-적재-앱이-s3에서-받기).

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

## 채우기 배치 측정용 더미 회원

`make_members.py`는 기준 데이터에서 주소를 뽑아 회원 N명(기본 10만)을 만든다. 표기 차이(서울 표기, 구 없음, 붙여쓰기, 상세주소, 지번, 오타, 서울 밖)를 섞고, 고른 동은 60% 실제 동, 10% 다른 동, 30% 비움으로 둔다. 결과(`members.tsv`, 정답 `truth.tsv`)도 레포 밖에 둔다.

```bash
python3 make_members.py ~/addressdb/out/address_reference.tsv ../../src/main/resources/deliveryfee/seoul-dongs.csv ~/addressdb/members
```

로컬 DB에 넣고 배치를 켠 서버를 띄우면, 시작할 때 표준 주소가 빈 회원을 채운다.

```bash
mysql --local-infile=1 -h127.0.0.1 -usetty -psetty setty -e "LOAD DATA LOCAL INFILE '$HOME/addressdb/members/members.tsv' INTO TABLE members CHARACTER SET utf8mb4 FIELDS TERMINATED BY '\t' (id, login_id, password, role, phone_number, address, dong_code)"
SETTY_ADDRESS_BACKFILL_ENABLED=true SETTY_ADDRESS_BACKFILL_BATCH_SIZE=500 ./gradlew bootRun
```

## dev·prod 적재 (앱이 S3에서 받기)

레포가 공개라 기준표는 비공개 S3에만 두고, 앱이 시작할 때 표가 덜 차 있으면 한 번 받아 넣는다(`AddressReferenceLoader`).

- 앱이 뜬 뒤 전용 스레드에서 돈다. 기동과 헬스 체크를 막지 않는다.
- 파일 전체를 메모리·디스크에 두지 않는다. S3 스트림 → gunzip → 줄 → 2,000줄씩 INSERT로 흘려 넣는다.
- S3를 두 번 읽는다. 첫 번째는 SHA-256만 확인하고, 두 번째는 같은 ETag일 때만 받아 넣는다. 해시가 다르면 넣지 않는다.
- MySQL `GET_LOCK`으로 무중단 배포 중 두 슬롯 중 한쪽만 넣는다. 다른 쪽은 건너뛴다.
- 표 행 수가 `rows` 이상이면 건너뛴다. 중간에 멈췄으면 다음 시작 때 이어 넣는다. 이미 있는 관리번호는 그대로 둔다.
- 해시가 다르거나, 받지 못하거나, 시간(`timeout`)이 넘으면 경고 로그만 남긴다. 앱은 그대로 동작하고 주소 해석·묶음 배송 칸만 비어 있다.

### 1. 파일 만들기 (레포 밖)

`build_reference.py --with-coords`로 만든 TSV를 관리번호 순으로 정렬해 압축한다. 정렬하면 PK 순서로 들어가 표가 덜 쪼개진다. `-n`은 파일 이름·시각을 빼서 같은 내용이면 해시가 같게 한다.

```bash
LC_ALL=C sort -t$'\t' -k1,1 address_reference.tsv > sorted.tsv
gzip -9 -n -c sorted.tsv > address_reference.tsv.gz
shasum -a 256 address_reference.tsv.gz   # → sha256
wc -l < sorted.tsv                       # → rows
```

- 형식: UTF-8, 탭 구분, 줄 끝 LF, V7 칸 순서 16칸(`load_reference.sql`과 같음), NULL은 `\N`.
- 2026-09 서울 전체: 523,227줄, 압축 11.16MB(원본 70.76MB).
- 시연용 상도동만: `awk -F'\t' '$7=="동작구" && $8=="상도동"' sorted.tsv`로 자른 뒤 같은 방법으로 압축한다(5,950줄, 0.13MB). 나중에 전체 파일로 바꾸면 빠진 줄만 이어 넣는다.

### 2. 올리기

- 공개 읽기가 없는 버킷(또는 접두사)에 올린다. 매물 이미지 버킷처럼 공개 URL로 읽히는 곳에 두지 않는다.
- EC2 역할에 그 키의 `s3:GetObject` 권한을 준다.

### 3. 켜기 (dev 먼저)

prod에서 켜기 전에 `df -h`로 디스크 여유와 binlog 보존 설정(`binlog_expire_logs_seconds`)을 확인한다. 표가 약 200MB, binlog가 66MB(기본 약 30일 보존) 늘고, 일일 덤프도 그만큼 커진다.

`/opt/setty/setty.env`에 넣고 배포(재시작)한다.

```
SETTY_ADDRESS_REFERENCE_LOAD_ENABLED=true
SETTY_ADDRESS_REFERENCE_LOAD_BUCKET=<비공개 버킷>
SETTY_ADDRESS_REFERENCE_LOAD_KEY=<키>/address_reference.tsv.gz
SETTY_ADDRESS_REFERENCE_LOAD_SHA256=<64자>
SETTY_ADDRESS_REFERENCE_LOAD_ROWS=523227
```

선택: `SETTY_ADDRESS_REFERENCE_LOAD_TIMEOUT`(기본 `PT10M`, 두 번 받기와 DB 넣기 전체), `SETTY_ADDRESS_REFERENCE_LOAD_BATCH_SIZE`(기본 2000, 최대 4000).

로그로 확인한다.

| 로그 | 뜻 |
|---|---|
| `주소 기준표를 S3에서 적재했습니다 … after=523227` | 넣음 |
| `주소 기준표가 이미 차 있어 건너뜁니다` | 이미 다 있음. 켜 둔 채로 두어도 시작마다 `COUNT(*)` 한 번(로컬 약 40ms)뿐이다 |
| `다른 인스턴스가 주소 기준표를 적재하고 있어 건너뜁니다` | 다른 슬롯이 넣는 중 |
| `주소 기준표를 적재하지 못했습니다` (WARN) | 해시·권한·시간·형식 문제. 원인을 고치고 다시 시작하면 이어 넣는다 |

### 4. 켜는 순서

1. 기준표 적재를 켜고, 위 로그로 다 들어간 것을 확인한다.
2. 회원 주소 채우기(`SETTY_ADDRESS_BACKFILL_ENABLED=true`)를 켜고 다시 시작한다. 기준표가 덜 찬 상태에서 켜면 못 맞춘 회원이 생긴다.
3. 매물 우편번호 구역 채우기(`SETTY_LISTING_ZIP_ZONE_BACKFILL_ENABLED`)는 회원 주소 채우기가 끝난 뒤 켠다.

### 자원 (2026-11-11 로컬 측정, 서울 전체 523,227줄)

| 항목 | 노트북(Docker MySQL 8.4.11, 버퍼 풀 128MB) | 운영 추정 |
|---|---|---|
| 걸린 시간 | 13.9~15.6초(2,000줄씩). 500줄씩 16.7초, 4,000줄씩 14.9초 | 노트북의 2~5배, 1분 안팎(추정). S3 두 번 받기 11MB×2는 같은 리전이라 수 초 |
| 앱 메모리 | 커밋된 힙 +16MB, 프로세스 RSS +6.5MB. 힙 상한 160MB에서도 정상 적재 | 같은 수준. 한 번에 들고 있는 것은 2,000줄 묶음과 64KB 버퍼뿐 |
| DB 디스크 | 표 파일 201MB, binlog +66MB | 표 파일 약 200MB. 운영 MySQL에 binlog가 켜져 있으면 +66MB(보관 기간이 지나면 지워짐) |
| DB 버퍼 풀 | 넣는 동안 표 페이지가 버퍼 풀을 거쳐 다른 페이지를 잠시 밀어낸다 | 운영 버퍼 풀 크기 미확인. 표는 가입·주소 변경·주문 확정 때 PK·색인 조회만 한다 |

- 기준일을 바꾼 새 파일은 빠진 관리번호만 넣고 기존 줄은 고치지 않는다. 바꾸려면 표를 비우는 일을 따로 정한다.
