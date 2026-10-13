# SSE 연결 상한 부하 검증

Node 22 이상에서 별도 라이브러리 없이 실행한다. 서버의 실제 인증 경로로 연결하고, 기사별 2개·전체 상한·정리 후 재등록을 확인한다. 전체 상한과 같은 수의 정상 연결을 유지하면서 목록 API 지연을 측정한다. 로컬 서버의 PID를 전달하면 `ps`의 RSS와 Java `jcmd GC.heap_info`의 힙 사용량도 수집한다.

## 검증 조건

- 전체 상한이 1,000이면 `--connections 1000`을 사용한다. 다른 상한에서는 같은 값으로 변경한다.
- N개 연결에 서로 다른 기사 N명, 상한 초과·복구 확인에 추가 기사 2명이 필요하다. 기존 일반 기사 계정은 사용하지 않는다.
- 변경 이벤트 발생률 X=0으로 유지한다. heartbeat와 소수의 주기적 목록 조회만 포함한다. 이벤트 처리량과 모든 앱의 동시 목록 조회를 검증한 결과는 아니다.
- 기존 연결이 상한을 차지하고 있으면 목표 N에 도달하지 못할 수 있다. 스크립트는 다른 사용자의 연결을 강제로 종료하지 않는다.
- 초기 점검 연결을 닫은 뒤 heartbeat 2회에 해당하는 31초를 기다린다. 연결 정리는 TCP 종료와 동시에 완료된다고 가정하지 않는다.
- 기본 조건은 20초 분산 연결, 목표 도달 후 45초 유지, 정리·복구 최대 40초다. 본 연결의 120초 타임아웃 전에 복구 확인을 마치도록 실행 시간을 제한한다.

## 테스트 전용 기사 데이터 준비

저장소 루트에서 실행한다. 파일에는 테스트 토큰이 들어가므로 `.local/`에 보관하며 Git에 추가하지 않는다. `prepare`는 파일만 생성하고 DB에는 접속하지 않는다.

```bash
node server/performance/sse-connection-load.mjs prepare \
  --drivers 1002 --output .local/sse/load-fixture
```

생성 파일은 기사별 토큰 `tokens.private.json`, 등록용 `seed.private.sql`, 이번 기사만 삭제하는 `cleanup.private.sql`이다. 로그인 ID는 실행마다 생성한 접두사를 사용한다. 로그인용 비밀번호는 설정하지 않으며 부하 검증은 생성한 Bearer 토큰을 사용한다.

대상 테스트 DB를 확인한 뒤 등록 SQL을 실행한다. `TEST_DB_USER`와 `TEST_DB_NAME`은 실제 테스트 DB 값으로 변경하며 비밀번호는 프롬프트에 입력한다. 로컬과 dev의 fixture를 별도로 생성한다.

```bash
mysql --batch --skip-reconnect -u TEST_DB_USER -p TEST_DB_NAME \
  < .local/sse/load-fixture/seed.private.sql
```

## 로컬 Spring Boot에 실행

Java 21과 DB가 준비된 Spring Boot를 실행한다. `--pid`에는 해당 Spring Boot의 PID를 사용하며, `--jcmd`는 같은 JDK의 실행 파일이다. PID 없이도 연결·API 검증은 실행되지만 서버 메모리는 측정하지 않는다.

```bash
node server/performance/sse-connection-load.mjs run \
  --base http://127.0.0.1:18080 \
  --token-file .local/sse/load-fixture/tokens.private.json \
  --connections 1000 --ramp-seconds 20 --hold-seconds 45 \
  --pid SPRING_PID --jcmd JAVA_21_HOME/bin/jcmd \
  --output .local/sse/local-load-result
```

dev는 `--base https://www.setty.cloud/dev`로 변경한다. 로컬 PID는 dev 서버의 메모리를 나타내지 않으므로 원격 실행에서는 전달하지 않는다. dev 메모리는 같은 시간대에 서버에서 별도로 수집한다.

## 판정과 결과 파일

정상 연결 N개 도달·유지, 기사별 초과의 429, 전체 초과의 503, 정상 슬롯 복구, 목록 API 200과 배열 응답, 예상하지 못한 단절 0건을 모두 만족해야 성공이다. 상한 초과 시 신규 요청이 200으로 열려도 실패한다. 실패 시 프로세스 종료 코드는 1이며 사유를 기록한다.

- `summary.json`: 성공 여부, 429·503 판정, 복구 시도 수·시간, API P50·P95·P99, 자원 표본.
- `samples.jsonl`: 구간별 클라이언트 활성 연결 수, 로컬 서버 RSS·힙 표본.
- `api.jsonl`: 목록 조회별 구간·상태·응답 시간.

강제 GC를 실행하지 않는다. 연결 종료 직후 힙이 남는 것만으로 누수라고 판단하지 않는다. 스크립트가 관측하는 활성 연결은 클라이언트 기준이며 서버 연결 맵의 개수를 직접 조회한 값은 아니다. 수치 SLO가 없다면 API 지연과 메모리는 관측값으로 보고한다.

검증 후 생성한 기사만 정리한다. 모든 SSE 스트림을 닫고 정리 대기까지 완료한 뒤 같은 DB에서 실행한다.

```bash
mysql --batch --skip-reconnect -u TEST_DB_USER -p TEST_DB_NAME \
  < .local/sse/load-fixture/cleanup.private.sql
node --test server/performance/sse-connection-load.test.mjs
```

스크립트 테스트는 작은 모의 HTTP 서버에서 상한·복구 판정, 잘못된 초과 응답의 실패 판정, 연결 실패를 확인한다. 실제 Spring Boot의 1,000개 검증을 대신하지 않는다.
