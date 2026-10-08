# 매물 목록 부하 테스트 준비

Gatling Java SDK의 **1명·GET 1회 스모크 테스트**와 **매물 100개 준비 SQL**이다. SQL은 수동 실행용이며 앱 시작·CI·스키마 마이그레이션에 포함되지 않는다.

## 스모크 실행

Java 21을 준비하고 `server/`에서 실행한다. IntelliJ에서는 Gradle 프로젝트를 다시 불러온다.

```bash
./gradlew compileGatlingJava
./gradlew gatlingRun --simulation setty.performance.ListingSmokeSimulation
```

- 대상: `GET https://www.setty.cloud/dev/api/listings`. 인증·특수 Host·SSM 터널·로컬 DB 불필요.
- 성공: 요청 1건, HTTP 200, `items` 필드 존재, 실패 0건. **매물 개수는 별도 확인**한다.
- 보고서: `server/build/reports/gatling/`. Git에서 제외되며 `clean` 전에 필요한 결과를 보존한다.

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

### 4 실험 종료 후 정리

결과를 보존한 뒤 같은 셸에서 `run_fixture_sql fixtures/cleanup-listings-100.sql`로 시험한다. 삭제 시도 건수는 사진 100·매물 100·판매자 1이며, 롤백 후 남은 건수는 **매물 100·판매자 1**이다. 실제 삭제는 3단계에서 입력 파일을 `fixtures/cleanup-listings-100.sql`로 바꿔 실행한다. 삭제 후 남은 건수는 **0·0**이어야 한다.

- 생성·정리는 **새 연결의 배치 모드**로 실행한다. 오류 시 멈추고 원인을 확인하며, 대화형 `SOURCE`나 `--force`는 사용하지 않는다. 오류 후 연결 종료로 미커밋 변경을 롤백하는 절차다.
- 정리는 이 100개 데이터셋 전용이다. 표식·상태·건수가 다르거나 주문·찜·정산이 있으면 중단한다. SQL 임시 파일 삭제만으로 DB 데이터가 지워지지는 않는다.

## 검증 범위

2026-10-08 dev MySQL 8.4.11에서 최초 SQL의 생성·롤백·저장과 API 200·매물/고유 ID/대표 사진 URL 각 100개를 확인했다. **확인값을 실행 시 전달하도록 변경한 SQL과 실제 정리 작업은 실행 검증 전**이다.

다음은 매물 100 → 1,000 → 5,000개 탐색이다. 순차 사용자 1명·응답 후 1초 대기·워밍업 20회·본 측정 100회·조건별 3회 반복 시나리오는 아직 구현 전이다.
