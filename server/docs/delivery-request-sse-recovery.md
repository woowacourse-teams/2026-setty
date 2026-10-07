# 배송 요청 SSE 복구

- 상태: 정책 확정, 백엔드 재시작 복구 검증 완료
- 범위: 첫 번째 SSE 작업인 서버 재시작 후 기사 요청 목록 복구

## 확정 정책

공유 정책의 원문은 [배송 정책](../../docs/policy/delivery.md), 정책 변경 절차는 [정책 문서 운영 가이드](../../docs/policy/policy-guide.md)를 기준으로 삼는다. 목록 변경 이벤트의 구현 계약은 [DeliveryRequestsChanged 문서](event/DeliveryRequestsChanged.md)를 참조한다. 이번 작업은 기존 정책 구현과 검증이며 공유 정책 원본은 변경하지 않는다.

- 채택: SSE 재연결이 성공하면 기존 `GET /api/delivery/requests`로 현재 목록을 다시 조회한다. SSE 이벤트 이력 저장·재생은 도입하지 않는다.
- 유지: 단일 서버, Spring MVC, 메모리 기반 활성 연결 관리, DB 커밋 이후 목록 변경 신호 전송.
- 유지: 기존 인증, 목록·수락 API, 서버 정렬, 앱의 세션 내 로컬 거절 필터.
- 비범위: 차종별 전송, 재연결 jitter, 느린 연결 전송 격리, SSE 이벤트 저장·재생, 다중 서버 전달.
- 조회 실패 처리: SSE 연결 성공과 목록 복원 성공을 별개로 본다. 현재 구현은 목록 조회 실패를 화면 오류와 수동 재시도 버튼으로 표시한다. 재연결 후 조회가 실패해도 SSE 연결 자체는 유지되므로, 다음 변경 신호가 오지 않으면 자동 재조회는 보장되지 않는다. 이번 범위에서는 새 자동 재시도 주기와 화면·앱 생명주기 규칙을 추가하지 않고 기존 수동 재시도 경로를 유지한다. 이 선택은 공유 정책 변경이 아니라 구현 범위 결정이며, 재연결 직후 목록 실패 시나리오의 실제 앱 검증은 확인 필요다.

SSE 발행과 전달 방식의 구현 근거는 [DeliveryRequestsChanged 구현 문서](event/DeliveryRequestsChanged.md)에 둔다. 위 결정은 새 공유 정책을 추가하지 않는 구현 범위 결정이다.

## 계획

1. 재연결 뒤 기존 목록 API로 최신 상태를 복구하는지 검증한다. 서버 재시작 복구는 API 반복 측정과 사용자 기기 관찰로 확인했다.
2. 서버가 유지된 채 앱의 SSE만 끊고 생성·수락·취소한 뒤 재연결하는 시나리오는 통합 테스트로 확인했다.
3. 서버 재시작 뒤 최신 목록 API 응답을 비교한다. 실제 화면 복귀·렌더링 측정은 별도 작업이다.
4. 앱 복귀 및 조회 실패 후 화면 재시도 동작은 별도 클라이언트 검증 범위다.
5. 백엔드 지표는 서버 API 준비 시점부터 재연결 후 최신 목록 API 응답까지의 P95와 API 응답 내 부적합 요청 잔존 건수다.

## 확인된 구현

- 앱은 화면이 포커스된 동안에만 SSE를 구독한다. SSE 연결 성공 때와 `delivery-requests-changed` 수신 때 모두 조회 콜백을 호출한다. 앱이 foreground로 돌아오면 재연결한다. 연결 재시도 간격은 1초에서 시작해 최대 30초까지 지수 증가한다. 근거: 서버 [DeliveryRequestEventStream.java](../src/main/java/setty/delivery/api/DeliveryRequestEventStream.java) 및 앱 [`deliveryRequestEvents.ts`](../../apps/driver/src/api/deliveryRequestEvents.ts).
- 요청 화면은 SSE 콜백에서 `useRequests.reload()`를 호출한다. `useRequests`는 조회 호출 순번이 더 오래된 응답을 버리고, 응답을 반영할 때 로컬 거절 항목을 제외한다. 목록 조회 오류는 화면 오류와 수동 재시도 버튼으로 노출된다. 근거: [`useRequests.ts`](../../apps/driver/src/features/requests/useRequests.ts), [`RequestListScreen.tsx`](../../apps/driver/src/features/requests/RequestListScreen.tsx), [`rejectedStore.ts`](../../apps/driver/src/features/requests/rejectedStore.ts).
- 서버의 `GET /api/delivery/requests`는 인증된 기사 요청이며 SQL 조건은 `status = 'REQUESTED' AND driver_id IS NULL`, 정렬은 `requested_at DESC, id DESC`다. 이는 현재 구현 조건으로 확인했으며 새 팀 정책으로 정의하지 않는다. 근거: [`DeliveryController.java`](../src/main/java/setty/delivery/api/DeliveryController.java), [`JdbcDeliveryQueryRepository.java`](../src/main/java/setty/delivery/persistence/JdbcDeliveryQueryRepository.java).
- SSE는 서버 메모리에서 연결을 관리하고, 생성·수락·취소 이벤트를 트랜잭션 커밋 후 보낸다. 앱 화면 이탈 시 구독을 정리하고 서버 종료 시 emitter와 heartbeat 작업을 정리한다. 근거: [서버 이벤트 구현](../src/main/java/setty/delivery/api/DeliveryRequestEventStream.java), [이벤트 계약](event/DeliveryRequestsChanged.md), [`RequestListScreen.tsx`](../../apps/driver/src/features/requests/RequestListScreen.tsx).

## 코드·문서 대조

- 개인 계획 1번은 현재 구현에 재연결 후 목록 재조회와 연결 정리가 있다고 적고 검증·측정을 남은 작업으로 둔다. 코드와 일치한다. 이번 실행 범위는 사용자의 작업 지시에 따라 이 1번만 진행한다. 계획의 2~4번은 이번 범위 밖이다.
- [API 매핑](../../apps/docs/api-mapping.md)은 SSE 변경 이벤트와 연결 재수립 시 목록을 재조회하고, foreground에서만 연결하며 1~30초 간격으로 재연결한다고 설명한다. 확인한 앱 코드와 일치한다.
- 정책 원본은 SSE 연결 성공과 목록 조회 성공의 구분, 조회 실패 시 화면 복구 동작을 정하지 않는다. 앱은 연결 성공 뒤 조회를 시도하며, 조회 실패 시 오류와 수동 재시도를 제공한다. 이 구현 사실과 제약을 이 문서에 기록한다.
- 확인된 불일치: 없음. 수동 재시도 동작의 실제 기기 화면 확인은 검증 전이다.

## 검증 기준 및 결과

### 검증 기준

- 단절 중 생성된 요청은 나타나고, 다른 기사가 수락하거나 구매자가 취소한 요청은 사라진다.
- 서버 재시작 시 DB와 테스트 데이터는 보존한다. 단일 서버 중단 중에는 해당 서버 API로 상태를 변경하지 않는다.
- 앱 복귀 시 재연결·재조회가 일어나고 중복 연결·타이머가 남지 않는다.
- 재조회 중 변경 신호가 오거나 응답이 역순으로 도착해도 최신 응답이 최종 화면을 결정한다.
- 재연결 직후 조회 실패는 오류 화면과 재시도 동작으로 구분해 확인한다. SSE 연결 성공만으로 목록 복원 성공이라 기록하지 않는다.
- 목록 불일치는 현재 서버 목록에 기존 로컬 거절 필터를 적용한 값과 비교한다. 로컬에서 의도적으로 숨긴 요청은 부적합 잔존 건수에서 제외한다.

### 결과

- 정책·코드 문서 대조: 완료. 확인된 구현은 위 `확인된 구현`에 기록했다.
- 통과: `DeliveryRequestEventStreamTest`, `DeliveryRequestsChangedPublisherTest`.
  - 명령: `GRADLE_OPTS='-Dorg.gradle.native=false' /Users/kangrae/.gradle/wrapper/dists/gradle-8.14-bin/38aieal9i53h9rfe7vjup95b9/gradle-8.14/bin/gradle --no-daemon test --tests setty.delivery.api.DeliveryRequestEventStreamTest --tests setty.delivery.application.DeliveryRequestsChangedPublisherTest`
  - 결과: `BUILD SUCCESSFUL`, 4개 Gradle task 실행, 테스트 task 통과.
  - 로그: `server/build/reports/tests/test/index.html`, `server/build/test-results/test/TEST-setty.delivery.api.DeliveryRequestEventStreamTest.xml`, `server/build/test-results/test/TEST-setty.delivery.application.DeliveryRequestsChangedPublisherTest.xml`.
- 앱 typecheck: 잠금 파일 기준 임시 복사본에서 통과.
  - 임시 소스 복사본 `/private/tmp/setty-driver-typecheck`에서 `npm ci --legacy-peer-deps --ignore-scripts --cache=/private/tmp/setty-driver-npm-cache` 후 `npm run typecheck` 실행 결과 종료 코드 0.
  - 워크스페이스의 `node_modules`는 lockfile과 다르다. 직접 실행한 `npm run typecheck`는 설치된 TypeScript 6.0.3의 `baseUrl` 진단으로 실패했고, deprecation을 무시한 재실행에서는 `@react-navigation/bottom-tabs` 누락과 그에 따른 암시적 `any`가 나왔다. lockfile은 TypeScript 5.9.3과 해당 패키지를 지정한다.
  - 임시 clean install에는 React peer 의존성 충돌을 우회하는 `--legacy-peer-deps`가 필요했다. 저장소의 package 파일과 `node_modules`는 수정하지 않았다.
- 추가 도구 검증: `python3 server/scripts/measure_sse_api_recovery.py ... --cycles 1` 스모크 실행은 2,878ms, API 잔존·누락 0건으로 종료했다. Python 구문 컴파일, 집계기 `node --check`, 30회 JSONL 집계, `git diff --check`를 통과했다.
- SSE 단절 중 변경 복구: `DeliveryRequestReconnectRecoveryIntegrationTest`가 격리 MySQL Testcontainers에서 통과했다. SSE 구독자가 없는 동안 요청을 생성하고, 하나를 다른 기사가 수락하고, 하나를 구매자가 취소한 다음 재구독 후 인증된 `GET /api/delivery/requests`를 호출했다. 목록에는 여전히 `REQUESTED`·미배정인 요청만 반환됐고, 수락·취소 요청의 잔존은 0건이었다. 1개 테스트 통과, 실패·오류·건너뜀 0건.
  - 명령(작업 디렉터리 `server`): `GRADLE_OPTS='-Dorg.gradle.native=false' ./gradlew --no-daemon test --tests setty.delivery.api.DeliveryRequestReconnectRecoveryIntegrationTest --tests setty.delivery.api.DeliveryRequestEventStreamTest --tests setty.delivery.application.DeliveryRequestsChangedPublisherTest`
  - 결과: `BUILD SUCCESSFUL`, SSE 관련 테스트 7개 통과, 실패·오류·건너뜀 0건. 로그: `server/build/reports/tests/test/index.html`, `server/build/test-results/test/TEST-setty.delivery.api.DeliveryRequestReconnectRecoveryIntegrationTest.xml`, `server/build/test-results/test/TEST-setty.delivery.api.DeliveryRequestEventStreamTest.xml`, `server/build/test-results/test/TEST-setty.delivery.application.DeliveryRequestsChangedPublisherTest.xml`.
- 실제 서버 재시작·기사 앱 관찰: 사용자는 DB를 유지하고 Spring만 재시작했다고 보고했다. 중단 중 기존 목록은 화면에 남았고, 중단 중 새로고침은 오류를 표시했다. 서버 재시작 뒤에는 참여한 모든 기사 기기에서 별도 조작 없이 약 1초 내 요청 목록으로 자동 복구됐고, API와 화면의 요청 ID가 일치했으며, 같은 앱 세션에서 로컬 거절한 요청은 숨겨졌다고 확인했다. 서버 복구 후 실패·시간 초과는 없었다. 이 관찰은 사용자가 실제 기기에서 확인한 복구 결과로 기록한다.
- 재연결 간격 참고: 앱의 1초는 연결 실패 후 첫 재연결 대기 시간이다. 재시도는 1·2·4초 순으로 증가해 최대 30초가 되며, SSE 변경 이벤트를 매초 전송한다는 뜻은 아니다. 근거: [`deliveryRequestEvents.ts`](../../apps/driver/src/api/deliveryRequestEvents.ts).
- API 대체 실측: 격리 DB를 유지하고 Spring 프로세스만 강제 종료(SIGKILL)·재기동하는 시나리오를 30회 실행했다. 환경은 macOS 로컬, Java 21.0.4, MySQL 8.4.11, `127.0.0.1:18080`이며 8080 사용자 서버에는 쓰기 요청을 보내지 않았다. 측정 중 8080의 health는 200, 인증 없는 목록 API는 401이었다. 사용자 앱 토큰을 가져오거나 실제 서버 데이터에 쓰지 않고 격리 환경을 사용했다. 테스트 데이터는 미배정 `REQUESTED` 1건, 배정된 `ACCEPTED` 1건, 미배정 `CANCELLED` 1건이었다. 재기동 뒤 인증된 목록 GET 첫 200부터, SSE 재연결 성공 후 같은 목록 API 응답이 그 ID 집합과 일치할 때까지를 API 대체 복원 시간으로 계산했다.
  - 결과: 완료 표본 30/30, Nearest-rank P95 **3,074ms**, 최소 2,216ms, 최대 3,142ms, 시간 초과 0회. SSE 재연결 실패 60회(회차당 2회), 재연결 후 목록 GET 실패 0회. readiness 확인용 GET 실패 2,212회는 서버 시작을 기다린 별도 polling 횟수로, 클라이언트 실패 시도에 합산하지 않았다.
  - 시간 분해: 프로세스 재시작부터 첫 목록 API `200`까지 P95 4,847ms(중앙값 4,201ms), API 준비부터 SSE 재연결까지 P95 3,064ms, SSE 연결 성공부터 목록 API 응답까지 P95 22ms(최대 24ms)였다. 복원 P95의 대부분은 서버 시작이 아니라 1·2초 재시도 실패 뒤 다음 재연결 대기에서 발생했다.
  - 각 회차의 API 목록은 `[1]`로 서버 목록과 일치했다. 부적합 API 잔존 최대 0건, 기대 요청 누락 최대 0건. 테스트 데이터의 수락 완료·취소 2건은 목록에서 제외됐다. 이 수치는 화면 목록이 아니라 재연결 클라이언트의 API 응답을 비교한 결과다.
  - 원시 기록: [30회 JSONL](evidence/delivery-request-sse-api-recovery-2026-10-07.jsonl). 집계: `node apps/driver/scripts/summarize-sse-recovery.mjs server/docs/evidence/delivery-request-sse-api-recovery-2026-10-07.jsonl`. 재현 도구: [`measure_sse_api_recovery.py`](../scripts/measure_sse_api_recovery.py). Spring별 로그는 측정 호스트의 `/private/tmp/setty-sse-api-measure-20261007/` 아래에 저장했다.
- 측정 범위: 이번 P95와 잔존 건수는 백엔드 API 기준이다. 화면 렌더링 P95는 별도 측정 대상으로 분리한다. 사용자 기기에서 관찰한 약 1초 복구는 정성 결과이며 API 반복 측정값과 합산하지 않는다.
- 별도 시나리오: 앱 복귀와 재연결 직후 목록 조회 실패·화면 응답 경합은 이번 백엔드 검증 범위가 아니다. 앱 화면 동작 검증은 별도 작업으로 관리한다.

### 재현·측정 입력

실제 서버 재시작 검증은 DB와 테스트 데이터를 유지하고 기사 앱을 foreground로 둔 상태에서 수행한다. 각 회차에서 서버 API가 다시 200을 반환한 시각과 화면 목록 일치 시각을 기록한다. 요청 카드에는 `deliveryId`가 표시되지 않으므로 테스트 데이터의 `itemName`을 고유하게 준비하고, 화면의 항목을 현재 서버 응답 ID와 대조해 `screenRequestIds`를 기록한다. 화면 기대 ID는 서버 응답에서 `locallyRejectedIds`를 뺀 집합이다.

JSONL의 각 줄은 한 회차다. 화면 직접 측정은 `screenMatchedAt`·`screenRequestIds`, API 대체 측정은 `scope: "isolated-api-client-proxy; screen-render-not-measured"`, `apiListMatchedAt`·`clientRequestIds`를 사용한다. `serverRequestIds`에서 `locallyRejectedIds`를 뺀 집합과 해당 측정 집합을 비교한다. API 대체 측정에는 로컬 거절 저장소나 화면 렌더가 포함되지 않으므로 화면 측정 JSONL과 혼합하지 않는다. 완료하지 못한 회차는 해당 matched 시각을 `null`, 시간 초과 회차는 `timedOut: true`로 남긴다. 다음 명령은 화면 직접 측정과 API 대체 측정의 범위를 구분해 완료 표본 P95(Nearest-rank), 최대 잔존·누락 수와 실패 시도를 집계한다.

```sh
node apps/driver/scripts/summarize-sse-recovery.mjs <측정 JSONL 경로>
```

집계기는 측정 기록의 계산 도구이며 화면이나 서버를 직접 제어하지 않는다. 앱 화면 직접 확인과 API-only 대체 측정은 별도 결과로 기록한다.

API 대체 재현 도구는 Spring jar와 `probe` 또는 `test` 이름이 포함된 로컬 MySQL DB만 허용한다. DB에는 전용 기사 계정·토큰과 고정 요청 데이터를 미리 준비한다. DB 환경 변수와 실행 예시는 도구의 도움말을 참고한다.

```sh
SETTY_SSE_PROBE_DB_URL=jdbc:mysql://127.0.0.1:3306/setty_probe \
SETTY_SSE_PROBE_DB_USERNAME=setty_probe \
SETTY_SSE_PROBE_DB_PASSWORD='<격리 DB 비밀번호>' \
python3 server/scripts/measure_sse_api_recovery.py \
  --jar server/build/libs/server-0.0.1-SNAPSHOT.jar \
  --token-file /path/to/disposable-driver-token \
  --output /path/to/api-recovery.jsonl \
  --logs-dir /path/to/spring-logs \
  --cycles 30
```
