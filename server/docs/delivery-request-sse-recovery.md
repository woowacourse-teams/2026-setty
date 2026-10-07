# 배송 요청 SSE 복구

- 상태: 정책 확정, 작업 진행 중
- 범위: 첫 번째 SSE 작업인 서버 재시작 후 기사 요청 목록 복구

## 확정 정책

공유 정책의 원문은 [배송 정책](../../docs/policy/delivery.md), 정책 변경 절차는 [정책 문서 운영 가이드](../../docs/policy/policy-guide.md)를 기준으로 삼는다. 목록 변경 이벤트의 구현 계약은 [DeliveryRequestsChanged 문서](event/DeliveryRequestsChanged.md)를 참조한다. 이번 작업은 기존 정책 구현과 검증이며 공유 정책 원본은 변경하지 않는다.

- 채택: SSE 재연결이 성공하면 기존 `GET /api/delivery/requests`로 현재 목록을 다시 조회한다. SSE 이벤트 이력 저장·재생은 도입하지 않는다.
- 유지: 단일 서버, Spring MVC, 메모리 기반 활성 연결 관리, DB 커밋 이후 목록 변경 신호 전송.
- 유지: 기존 인증, 목록·수락 API, 서버 정렬, 앱의 세션 내 로컬 거절 필터.
- 비범위: 차종별 전송, 재연결 jitter, 느린 연결 전송 격리, SSE 이벤트 저장·재생, 다중 서버 전달.
- 조회 실패 처리: SSE 연결 성공과 목록 복원 성공을 별개로 본다. 목록 조회 실패는 현재 앱의 오류 상태와 수동 재시도 버튼으로 드러내고, 사용자가 기존 목록 조회를 다시 요청한다. 이번 범위에서는 자동 조회 재시도 타이머를 추가하지 않는다. 별도 재시도 주기와 화면·앱 생명주기 정리 규칙을 새로 만들 근거가 없고, 기존 오류 복구 경로를 사용할 수 있기 때문이다. 따라서 재연결 직후 일시 오류가 나면 사용자의 재시도가 필요하다.

SSE 발행과 전달 방식의 구현 근거는 [DeliveryRequestsChanged 구현 문서](event/DeliveryRequestsChanged.md)에 둔다. 위 결정은 새 공유 정책을 추가하지 않는 구현 범위 결정이다.

## 계획

1. 기존 재연결 후 재조회, 앱 복귀, 연결 정리와 응답 경합 처리를 검증한다.
2. 연결 단절 중 생성·다른 기사의 수락·구매자 취소와 서버 재시작을 각각 재현한다.
3. 재연결 시점의 현재 수락 가능 목록 및 화면 목록을 비교한다. 화면 기대값은 서버 목록에 기존 로컬 거절 필터를 적용한 값이다.
4. 조회 실패 후 오류·수동 재시도 동작을 확인한다.
5. 서버 처리 가능 시점부터 화면 목록 일치까지의 P95와, 재조회 완료 후 수락 불가능한 요청의 화면 잔존 건수를 표본 수·환경·반복 횟수·실패·시간 초과 건수와 함께 기록한다.

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
- 실제 단절·서버 재시작·기사 앱 화면: 사용자가 1회 관찰했다고 보고했다. DB를 유지하고 Spring만 재시작했다. 중단 중 기존 목록이 화면에 남았고, 중단 상태에서 새로고침하면 오류가 표시됐다. 재시작 뒤 화면을 조작하지 않았는데 약 1초 후 요청 목록으로 자동 복구됐다. API 요청 ID와 화면 요청 ID는 같았다고 보고했다. 이는 SSE 재연결 성공 후 목록을 다시 조회하는 현재 구현과 일치하는 자동 복구의 정성 관찰이다.
- 관찰 한계: 약 1초는 사용자 추정값이며 서버의 첫 API `200` 시각과 화면 반영 시각을 각각 기록하지 않았다. API·화면의 원시 ID 배열과 로컬 거절 ID도 보존하지 않았다. 따라서 사용자 보고상 목록은 일치하지만 ID 집합과 필터 결과를 독립적으로 재계산할 수 없고, 잔존 건수는 확인 필요다. 중단 중 새로고침 오류는 적어도 하나의 실패한 목록 조회를 뜻하므로, 전체 회차 실패 횟수가 0인 것은 아니다. 사용자가 보고한 실패 없음은 서버 복구 이후의 실패가 없었다는 뜻인지 확인 필요다.
- 재연결 간격 참고: 앱의 1초는 연결 실패 후 첫 재연결 대기 시간이다. 재시도는 1·2·4초 순으로 증가해 최대 30초가 되며, SSE 변경 이벤트를 매초 전송한다는 뜻은 아니다. 근거: [`deliveryRequestEvents.ts`](../../apps/driver/src/api/deliveryRequestEvents.ts).
- 실측: 사용자 보고 관찰 1회에서 화면 자동 복구는 확인했으나, 정확한 `serverReadyAt`·`screenMatchedAt`과 원시 ID가 없어 이 기록은 집계 JSONL에 넣지 않는다. 따라서 P95와 잔존 건수는 확인 필요다. synthetic 입력 3행으로 집계기를 확인한 결과는 실측값에 포함하지 않는다.

### 재현·측정 입력

실제 서버 재시작 검증은 DB와 테스트 데이터를 유지하고 기사 앱을 foreground로 둔 상태에서 수행한다. 각 회차에서 서버 API가 다시 200을 반환한 시각과 화면 목록 일치 시각을 기록한다. 요청 카드에는 `deliveryId`가 표시되지 않으므로 테스트 데이터의 `itemName`을 고유하게 준비하고, 화면의 항목을 현재 서버 응답 ID와 대조해 `screenRequestIds`를 기록한다. 화면 기대 ID는 서버 응답에서 `locallyRejectedIds`를 뺀 집합이다.

JSONL의 각 줄은 한 회차다. 필수 필드는 `run`, `scenario`, `serverReadyAt`, `screenMatchedAt`, `serverRequestIds`, `screenRequestIds`, `locallyRejectedIds`, `failedAttempts`, `timedOut`이다. 완료하지 못한 회차는 `screenMatchedAt: null`, 시간 초과 회차는 `timedOut: true`로 남긴다. 측정 입력을 저장한 후 다음 명령으로 완료 표본의 P95(Nearest-rank), 최대 부적합 잔존 수, 누락 수와 재시도 정보를 집계한다.

```sh
node apps/driver/scripts/summarize-sse-recovery.mjs <측정 JSONL 경로>
```

집계기는 측정 기록의 계산 도구이며 화면이나 서버를 직접 제어하지 않는다. 앱 화면 직접 확인과 API-only 대체 측정은 별도 결과로 기록한다.
