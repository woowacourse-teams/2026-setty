# payment/ — 결제 (스프린트2)

이 파일은 `server/src/main/java/setty/payment/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`가 원본이다.

## 소유권·경계

- `platform`·`delivery`와 같은 최상위 계층이다. 결제 담당이 관리한다.
- `platform/**`은 **읽기만** 한다 (`OrderRepository`·`ListingRepository` 조회로 검증). 주문 상태 변경·배차 발행·선점 해제는 직접 하지 않고 **이벤트로 알린다** — `PaymentCompleted`/`PaymentFailed`를 발행하면 플랫폼(주문) 팀이 수신해 처리한다.
- `payment` → `platform` 은 **읽기 의존만** 둔다. `OrderService`를 직접 호출하지 않는다(이벤트 기반 단방향). 반대 방향(`order` → `payment`)도 만들지 않는다.
- 주문 내부 상태(PENDING/만료 등)는 읽지 않는다. 만료·선점 판정과 보상은 이벤트 수신 측 책임.

## 결제 흐름 (해피패스)

1. 플랫폼(주문) 팀이 구매신청 시 **PENDING 주문을 먼저 생성**해 `orderId`(내부 주문 id)를 발급한다. 프론트는 그 `orderId`로 토스 결제창을 띄운다.
2. 결제 후 토스가 브라우저를 **`GET /payments/success?paymentKey&orderId&amount`**(실패 시 `/payments/fail`)로 돌려보낸다. 이 경로는 `/api/**` 밖이라 인증을 타지 않는다 — 구매자는 토큰이 아니라 `orderId`로 파생하고, 진정성은 토스가 검증한 `paymentKey`로 보장된다.
3. `PaymentService`가 `orderId`로 주문·매물을 읽어 **매물 `totalPrice`를 재계산해 `amount`와 대조**한다(위변조 방지).
4. `TossPaymentClient`가 토스 승인을 호출한다 — **DB 트랜잭션 밖**. (외부 호출을 트랜잭션에 넣지 않는다.)
5. 승인되면 `PaymentRecorder`가 **하나의 트랜잭션**으로 `Payment` 저장 + `PaymentCompleted(orderId)` 발행을 처리한다. (payment는 주문을 만들지 않는다.)
6. 실패 복귀(`fail()`)는 `Payment(ABORTED)` 저장 + `PaymentFailed(orderId)` 발행. 처리 후 프론트 결과 페이지로 302 리다이렉트한다.

## 규칙

- 실제 결제는 붙이지 않는다. 토스 **테스트 시크릿키**로 승인만 흉내낸다.
- 예외는 `BusinessException` + `ErrorCode`만 사용한다 (`server/docs/exception-handling.md`).
- 주문당 결제 1행(upsert): `payments.order_id` UNIQUE. 실패(ABORTED) 후 재승인 시 같은 행을 `DONE`으로 전이하고, 이미 `DONE`이면 재승인 없이 멱등 반환한다. 동시 중복 저장은 `DataIntegrityViolationException` → `ALREADY_PAID`.
- 실패 저장을 위해 `payment_key`·`approved_at`은 NULL 허용.
- `PaymentCompleted`/`PaymentFailed`는 `common`의 팀 간 계약이다 — 필드 변경은 양 팀 합의가 필요하다.
- 스키마 변경은 `schema.sql`에만 추가한다 (`ddl-auto: validate`).

## 범위 밖 (추후 이슈)

- 결제 취소/부분취소·실제 환불, PENDING 주문 만료 처리, 판매자·배송원 포인트 정산.
- 이벤트 수신 측(주문 CONFIRMED 전이·`OrderRequested` 발행·PENDING 취소)은 플랫폼(주문) 팀 담당.
