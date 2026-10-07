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
- 테스트와 실제 시나리오 검증: 진행 중.
- 복원 시간 P95, 부적합 요청 잔존 건수, 표본 수, 환경, 반복 횟수, 실패·시간 초과 건수: 확인 필요.
