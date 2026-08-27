# platform/order/ — 주문 (플랫폼 팀 관리)

이 파일은 `server/src/main/java/setty/platform/order/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`가 원본이다.

## 소유권

- 플랫폼 팀이 관리한다. `orders.delivery_status`는 **배송 팀의 `DeliveryStatusChanged` 이벤트를 수신해 플랫폼이 갱신**한다 (#236에서 전환, 이전에는 배송 팀이 직접 UPDATE했다). 갱신 경로는 `DeliveryStatusChangedListener → SyncOrderDeliveryStatusService → Order.syncDeliveryStatus()` 하나뿐이다 — API로 배송 상태를 바꾸는 엔드포인트를 만들지 않는다. `orders.driver_id`는 현재 미사용(항상 NULL)이며 정리 예정.

## 내용물

- `Order` — 주문 엔티티. `@Table(name = "orders")` (ORDER는 SQL 예약어), listingId·buyerId·deliveryStatus·driverId
- `OrderController` — `POST /api/orders` / `GET /api/me/orders` / `GET /api/orders/{id}`
- `OrderService` — 주문 생성(중복 방지 + `OrderRequested` 이벤트 발행), 내 주문 조회
- `DeliveryStatusChangedListener` — `DeliveryStatusChanged` 수신 전용의 얇은 리스너. 위임만 한다
- `SyncOrderDeliveryStatusService` — 이벤트 검증·파싱 후 주문의 배송 상태를 동기화한다

## 규칙

- **1매물 1주문**: `uk_orders_listing_id` UNIQUE가 원천 봉쇄한다. 중복 주문은 3단 방어를 유지한다 —
  사전 체크(existsByListingId) → `saveAndFlush` → `DataIntegrityViolationException` catch 후 `ALREADY_ORDERED` 변환.
  `save`로 바꾸면 UNIQUE 위반이 커밋 시점(서비스 밖)에 터져 500이 된다.
- `OrderRequested` 이벤트는 `setty/common/`의 팀 간 계약이다. 필드 추가·삭제는 배송 팀 합의 없이 하지 않는다.
  발행은 주문 저장과 **같은 트랜잭션에서 동기**로 한다 — 리스너 실패 시 주문 롤백이 의도된 동작이다.
- 조회 응답의 매물 정보는 orders에 스냅샷으로 저장하지 않고 listing을 2차 조회해 조합한다 (DEC-06).
  매물 검증·구매 신청 등록은 `ListingService.registerPurchaseRequest()`를 통하고,
  조회 조합은 `ListingRepository`를 **읽기 전용**으로만 쓴다 (DEC-05 합의). listing을 여기서 수정하지 않는다.
- 본인 주문만 조회할 수 있다. 남의 주문 id는 403이 아니라 **404 `ORDER_NOT_FOUND`**로 응답한다 (주문 존재 여부를 노출하지 않기 위함).
- 배송 상태 동기화는 **순방향 전이만** 허용한다 (`Order.syncDeliveryStatus()` — enum 선언 순서 기준). 중복·역행 이벤트는 예외 없이 무시한다 (멱등). 이 방어를 제거하면 이벤트 재전송·순서 꼬임 시 상태가 뒤로 간다.
- 스키마 변경은 `schema.sql`에 멱등 SQL 추가로만 한다 (`ddl-auto: validate`).
