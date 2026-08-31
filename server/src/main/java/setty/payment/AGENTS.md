# payment/ — 결제 (스프린트2)

이 파일은 `server/src/main/java/setty/payment/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`가 원본이다.

## 소유권·경계

- `platform`·`delivery`와 같은 최상위 계층이다. 결제 담당이 관리한다.
- `platform/**`은 **읽기만** 한다 (`ListingRepository`·`OrderRepository` 조회로 검증). 상태 변경은 직접 하지 않고 `OrderService.create()`에 위임한다 — 매물 선점·주문 저장·`OrderRequested` 발행이 모두 그 안에서 한 트랜잭션으로 일어난다.
- `payment` → `platform.order` **단방향** 의존만 둔다. 반대 방향(`order` → `payment`)은 만들지 않는다.

## 결제 흐름 (해피패스)

1. 프론트가 매물 상세의 `totalPrice`/`title`로 토스 결제창을 띄운다(서버 무관). `orderId`는 클라이언트가 만든다.
2. 결제 후 `POST /api/payments/confirm { listingId, tossOrderId, paymentKey, amount }`.
3. `PaymentService`가 매물 `totalPrice`를 재계산해 `amount`와 대조한다(위변조 방지). 본인 매물·중복 주문도 선검증.
4. `TossPaymentClient`가 토스 승인을 호출한다 — **DB 트랜잭션 밖**. (외부 호출을 트랜잭션에 넣지 않는다.)
5. 승인되면 `PaymentRegistrar`가 **하나의 트랜잭션**으로 `OrderService.create()` + `Payment` 저장을 처리한다.
   → 결제 확정 이후에만 주문이 생기고 `OrderRequested`가 발행된다.

## 규칙

- 실제 결제는 붙이지 않는다. 토스 **테스트 시크릿키**로 승인만 흉내낸다(항상 성공).
- 예외는 `BusinessException` + `ErrorCode`만 사용한다 (`server/docs/exception-handling.md`).
- 1주문 1결제: `payments.order_id` UNIQUE + 저장 시 `DataIntegrityViolationException` → `ALREADY_PAID`. 실제 중복 confirm은 대개 그 앞단(`OrderService.create()`의 `ALREADY_ORDERED`)에서 먼저 막힌다.
- 스키마 변경은 `schema.sql`에만 추가한다 (`ddl-auto: validate`).

## 범위 밖 (추후 이슈)

- 결제 실패/취소/부분취소, 미결제 주문의 매물 선점 만료, 판매자·배송원 포인트 정산.
