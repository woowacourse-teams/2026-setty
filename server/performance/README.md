# 매물 목록 부하 테스트 준비

Gatling Java SDK의 **단건 스모크·매물 수 증가 탐색 시나리오**와 **매물 100개 생성·1,000/5,000개 확장 SQL**이다. SQL은 수동 실행용이며 앱 시작·CI·스키마 마이그레이션에 포함되지 않는다.

## 스모크 실행

Java 21을 준비하고 `server/`에서 실행한다. IntelliJ에서는 Gradle 프로젝트를 다시 불러온다.

```bash
./gradlew compileGatlingJava
./gradlew gatlingRun --simulation setty.performance.ListingSmokeSimulation
```

- 대상: `GET https://www.setty.cloud/dev/api/listings`. 인증·특수 Host·SSM 터널·로컬 DB 불필요.
- 성공: 요청 1건, HTTP 200, `items` 필드 존재, 실패 0건. **매물 개수는 별도 확인**한다.
- 보고서: `server/build/reports/gatling/`. Git에서 제외되며 `clean` 전에 필요한 결과를 보존한다.

## 매물 수 증가 탐색

데이터 준비 후 `server/`에서 실행한다. `LISTING_COUNT`는 **이미 DB에 준비된 예상 매물 수**이며 100·1000·5000을 지원한다. 이 값만 바꿔도 DB 데이터가 늘어나는 것은 아니다.

```bash
LISTING_COUNT=100 ./gradlew gatlingRun \
  --simulation setty.performance.ListingCountSimulation \
  --run-description listings-100-run-1
```

- 사용자 1명, **워밍업 20회 → 본 측정 100회**, 매 응답 뒤 1초 대기. 1회 실행은 약 2분 + 응답 시간이며 고정 1 RPS가 아니다.
- HTTP 200과 `items` 개수를 매번 검사한다. 실패하면 중단하며, 총 120건·실패 0건이어야 유효한 실행이다.
- HTML 보고서의 **`listings-measurement` 행**에서 중앙값·p95·실패 건수를 본다. 전체 합계에는 워밍업이 포함된다.
- 요청별 시각·응답 시간·본문 크기는 `build/reports/listing-count/`의 CSV에 저장한다. `phase=listings-measurement`만 비교하며, 크기는 Gatling이 읽은 본문 바이트 수로 헤더·전송 압축 크기와 구분한다.
- CSV의 `request_id`로 서버의 전체·내부 구간 시간을 연결한다. [계측 범위·로컬 검증·dev 로그 조회](request-timing.md)를 참고한다. ID가 비어 있으면 해당 요청의 로그 연결은 확인할 수 없다.
- 첫 실행을 확인한 뒤 같은 조건으로 총 3회 실행하고 `run-1`을 `run-2`, `run-3`으로 바꾼다. HTML·CSV와 같은 시간대의 서버·DB 지표를 함께 보존한다.

기본 대상은 dev다. 시나리오 자체를 로컬에서 검증할 때만 `LISTING_BASE_URL=http://127.0.0.1:포트`로 바꿀 수 있다. 이 로컬 결과는 dev 성능 결과로 사용하지 않는다. 워밍업과 본 측정의 구분은 [요청별 통계 범위](https://docs.gatling.io/concepts/assertions/)를 사용한다.

## 데이터 준비와 정리

**dev EC2의 SSM 셸**에서 실행한다. MySQL 8.0.16 이상·InnoDB와 조회·삽입·삭제·임시 테이블 생성 권한이 필요하다. `fixtures/`를 서버에 복사하고 그 상위 디렉터리에서 실행한다.

데이터는 로그인 불가 전용 판매자 `s2-listing-001`, 판매 가능 매물 100개, 매물당 사진 메타데이터 1개다. 필드와 생성 시각 규칙은 [생성 SQL](fixtures/seed-listings-100.sql)에 고정했다. **S3 파일은 없으므로 이미지 표시 테스트에는 사용할 수 없다.** 데이터는 dev 목록에 노출되므로 팀의 기능 테스트·배포와 측정 시간을 조율한다.

### 1 대상 확인

`DEV_DB_USER`, `DEV_DB_NAME`을 실제 dev 값으로 바꾸고 비밀번호는 프롬프트에 입력한다.

```bash
mysql --batch --skip-reconnect -u DEV_DB_USER -p DEV_DB_NAME < fixtures/preflight.sql
```

신규 생성 조건은 **판매 가능 매물 0개·실험 판매자 0명·모두 InnoDB**다. 이미 준비됐으면 재생성하지 않고, 다른 매물은 지우지 않는다. 접속한 EC2와 앱의 DB 대상도 확인한다.

### 2 시험 실행

아래 두 확인값을 앞선 결과의 `database_name`, `mysql_hostname`으로 바꾼다. 호스트 값은 **접속 주소가 아니라 연결된 DB의 `@@hostname` 검사값**이며, 누락·불일치 시 중단한다.

```bash
fixture_init="SET @fixture_expected_database='DEV_DB_NAME', @fixture_expected_hostname='DEV_MYSQL_HOSTNAME';"
run_fixture_sql() {
  mysql --batch --skip-reconnect --skip-force --init-command="$fixture_init" \
    -u DEV_DB_USER -p DEV_DB_NAME < "$1"
}
run_fixture_sql fixtures/seed-listings-100.sql
```

매물·사진 100개씩 생성한 뒤 **기본값인 `ROLLBACK`으로 되돌린다.** 마지막 `*_after_run`은 매물 0·판매자 0이어야 한다. ID는 롤백해도 소모될 수 있다.

### 3 실제 저장

시험 통과 후 **같은 셸**에서 실행한다. 원본은 유지하고 임시 실행본만 `COMMIT`으로 바꾼다.

```bash
fixture_commit_sql=$(mktemp /tmp/setty-s2-seed-100.XXXXXX)
sed 's/^ROLLBACK;$/COMMIT;/' fixtures/seed-listings-100.sql > "$fixture_commit_sql"
run_fixture_sql "$fixture_commit_sql"
```

마지막 건수는 **매물 100·판매자 1**이다. API에서도 `items` 100개·중복 ID 없음·대표 사진 URL 100개를 확인한다.

각 조건 측정 후 아래 SQL을 같은 실행 함수로 시험한다. 기존 판매자·매물 필드·사진을 검사하며, 각 매물에 사진 메타데이터 1개를 유지한다.

| 확장 SQL (`fixtures/`) | 추가 매물/사진 | 롤백 전 매물/사진 | 롤백 후 마지막 세 건수 |
| --- | --- | --- | --- |
| `expand-listings-100-to-1000.sql` | 900/900 | 1,000/1,000 | 모두 100 |
| `expand-listings-1000-to-5000.sql` | 4,000/4,000 | 5,000/5,000 | 모두 1,000 |

시험 통과 후 위 명령의 입력 파일을 해당 확장 SQL로 바꿔 임시 COMMIT 실행본을 만든다. 저장 후 마지막 세 건수는 모두 목표 개수여야 한다. **기존 100개 전용 정리 SQL은 확장 데이터에 사용할 수 없다.**

### 4 실험 종료 후 정리

결과를 보존한 뒤 같은 셸에서 `run_fixture_sql fixtures/cleanup-listings-100.sql`로 시험한다. 삭제 시도 건수는 사진 100·매물 100·판매자 1이며, 롤백 후 남은 건수는 **매물 100·판매자 1**이다. 실제 삭제는 3단계에서 입력 파일을 `fixtures/cleanup-listings-100.sql`로 바꿔 실행한다. 삭제 후 남은 건수는 **0·0**이어야 한다.

- 생성·정리는 **새 연결의 배치 모드**로 실행한다. 오류 시 멈추고 원인을 확인하며, 대화형 `SOURCE`나 `--force`는 사용하지 않는다. 오류 후 연결 종료로 미커밋 변경을 롤백하는 절차다.
- 정리는 이 100개 데이터셋 전용이다. 표식·상태·건수가 다르거나 주문·찜·정산이 있으면 중단한다. SQL 임시 파일 삭제만으로 DB 데이터가 지워지지는 않는다.

## 검증 범위

2026-10-08 dev MySQL 8.4.11에서 최초 SQL의 생성·롤백·저장과 API 200·매물/고유 ID/대표 사진 URL 각 100개를 확인했다. **확인값을 실행 시 전달하도록 변경한 SQL과 실제 정리 작업은 실행 검증 전**이다.

시나리오는 컴파일과 임시 로컬 서버 검증을 통과했다: 워밍업 20건·본 측정 100건, 동시 요청 1건, 응답 후 1초 대기, CSV 기록 및 워밍업·본 측정에서 개수 불일치 시 중단을 확인했다. 2026-10-09 dev 1,000개 확장 SQL의 롤백·저장 출력을 확인했고, 5,000개 확장은 롤백 출력과 사용자 저장 완료 보고 이후 API 개수 검사로 확인했다. 100개·1,000개·5,000개 조건 각각 3회 측정에서 실행당 120건·실패 0건을 확인했다. 확장 데이터 정리는 후속 작업이다. 요청 ID·구간 계측 보완 후 dev 재측정은 아직 진행 전이다.

## 키워드 알림 발송 측정

매물 등록 1건이 구독자 전원에게 알림을 보내는 구조(설계: [server/docs/notification/keyword-notification.md](../docs/notification/keyword-notification.md))를 분리 전·후로 측정한다. 등록은 Gatling이 PC에서 dev로 보내고, 외부 푸시는 dev EC2 안에서 띄운 모의 서버가 받는다.

### 1 모의 푸시 서버 (dev EC2 SSM 셸)

`performance/mock-push/mock-push.mjs`를 서버에 복사하고 Node 20+로 실행한다. `DELAY_MS`는 FCM 왕복 실측값을 넣는다.

```bash
PORT=9000 DELAY_MS=50 OUT=/var/tmp/mock-push-received.csv node mock-push.mjs
```

- `POST /send`: 지연 뒤 200과 `failedNotificationIds`를 돌려준다. `FAIL_RATE=0.1`이면 10%를 실패로 돌려준다.
- `GET /stats`: 요청 수·메시지 수·첫/마지막 수신 시각·차이(ms). `POST /reset`으로 비운다.
- CSV에 요청마다 수신 시각(서버 시계)·건수를 남긴다.

앱은 `setty.env`에 아래를 넣고 재시작해야 모의 서버로 보낸다. 측정이 끝나면 되돌린다.

```
SETTY_NOTIFICATION_SENDER=mock
SETTY_NOTIFICATION_MOCK_PUSH_URL=http://127.0.0.1:9000
SETTY_NOTIFICATION_SEND_CONCURRENCY=8
SETTY_NOTIFICATION_SYNC_SEND=false
```

`SYNC_SEND=true`가 분리 전 구조(등록 트랜잭션 안에서 한 명씩 동기 발송)다.

### 2 구독자 데이터 (dev EC2 SSM 셸)

매물 목록 실험과 같은 규칙이다. 판매자 토큰은 저장소에 두지 않고 실행 시 전달한다. `uuidgen`으로 만든 36자 값을 Gatling의 `SELLER_TOKEN`에도 같은 값으로 쓴다.

```bash
fixture_init="SET @fixture_expected_database='DEV_DB_NAME', @fixture_expected_hostname='DEV_MYSQL_HOSTNAME', @fixture_seller_token='SELLER_TOKEN_UUID';"
run_fixture_sql fixtures/seed-subscribers-1000.sql
```

| SQL (`fixtures/`) | 결과 |
| --- | --- |
| `seed-subscribers-1000.sql` | 판매자 `s3-seller-001` 1명, 구독자 `s3-sub-000001`~`001000`, 키워드 `s3-notify` 구독 1,000건 |
| `expand-subscribers-1000-to-10000.sql` | 구독자·구독 10,000 |
| `expand-subscribers-10000-to-100000.sql` | 구독자·구독 100,000 |
| `cleanup-subscribers.sql` | 측정 중 등록된 매물·사진 행·알림, 구독, 전용 회원 삭제. S3 객체는 남는다 |

기본은 ROLLBACK 시험이고, 저장은 임시 COMMIT 실행본으로 한다(위 "실제 저장" 참고). 구독자가 `s3-notify`를 구독하므로 Gatling이 등록하는 제목 `[S3-N001] s3-notify desk NNNNNN`에 전원이 매칭된다.

### 3 등록 부하 (PC, `server/`)

```bash
export SELLER_TOKEN='SELLER_TOKEN_UUID'
STRUCTURE=async SUBSCRIBERS=1000 REGISTER_MODE=single REGISTER_REPEATS=30 \
  ./gradlew gatlingRun --simulation setty.performance.ListingRegisterSimulation
STRUCTURE=async SUBSCRIBERS=1000 REGISTER_MODE=burst BURST_USERS=10 \
  ./gradlew gatlingRun --simulation setty.performance.ListingRegisterSimulation
```

- `single`: 사용자 1명이 워밍업 5회 → 측정 30회 등록. 요청 사이 `REGISTER_PAUSE_SECONDS`(기본 3초)를 쉬어 앞 등록의 발송이 겹치지 않게 한다. 구독자가 많으면 늘린다.
- `burst`: `BURST_USERS`명이 동시에 1건씩 등록.
- `STRUCTURE`·`SUBSCRIBERS`는 CSV 라벨이다. 분리 전은 `sync`, 분리 후는 `async`로 적는다.
- 사진은 `src/gatling/resources/notification-tiny.png`(1×1, 69바이트)로 고정해 업로드 시간을 최소화한다. dev S3에 실제 올라간다.
- 보고서 `build/reports/gatling/`의 `register-measurement` 행에서 등록 P95를 본다. 요청별 응답 시간·매물 ID·`request_id`는 `build/reports/listing-register/`의 CSV에 남는다.

### 4 발송 시간 집계 (dev EC2 SSM 셸)

```bash
mysql --batch -u DEV_DB_USER -p DEV_DB_NAME < notification-metrics.sql
```

매물마다 알림 수, 상태별 건수, 커밋부터 마지막 발송까지(`commit_to_last_sent_s`), 첫 발송부터 마지막 발송까지(`first_to_last_sent_s`)를 초 단위로 낸다. 모의 서버의 `GET /stats`와 CSV로 교차 확인한다. DB 커넥션 대기·타임아웃은 CloudWatch의 HikariCP 지표(#331)를 같은 시간대로 본다.

### 순서

구독자 1,000 → `sync` single/burst → `async` single/burst → 10,000으로 확장 → 반복 → 100,000(시간 보고 `sync`는 생략 가능) → `async`에서 `SEND_CONCURRENCY` 4/8/16으로 재측정 → cleanup.
