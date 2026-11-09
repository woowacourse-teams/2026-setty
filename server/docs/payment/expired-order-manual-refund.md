# 끝난 주문의 결제 환불과 수동 확인

토스에서 승인됐지만 우리 주문이 이미 만료(`EXPIRED`)되거나 판매자 취소(`CANCELLED`)된 결제는 자동으로 환불한다. 사람이 볼 것은 자동 환불이 멈춘 건과 자동 경로에서 빠진 건이다.

## 자동 처리

1. 승인 요청 전에 `payments`에 `UNKNOWN` 행을 `paymentKey`와 함께 남긴다. 토스가 명확히 거절하면 지우고, 승인 여부를 끝내 확인하지 못하면 남긴다. 같은 주문의 다른 결제 키 승인은 `409 PAYMENT_IN_PROGRESS`로 막는다.
2. 결제 복구 배치(`PaymentRecoveryScheduler`, 기본 5분, 결제 전용 스레드)가 만든 지 10분 넘은 `UNKNOWN`을 `GET /v1/payments/{paymentKey}`로 조회한다. 승인됐으면 `DONE`으로 기록해 `PaymentCompleted`를 낸다. 승인되지 않았거나 토스에 없으면 행을 지운다. 아직 처리 중이거나 조회에 실패하면 다음 주기에 다시 묻는다.
3. 주문이 이미 끝났으면 `OrderService#publishOrderConfirmed`가 [`PaymentRefundRequested`](../event/PaymentRefundRequested.md)를 내고, 결제 모듈이 `payment_refunds`에 `REQUESTED`로 기록한다.
4. 같은 배치가 `REQUESTED` 환불을 `POST /v1/payments/{paymentKey}/cancel`로 전액 취소한다. 환불 행마다 정해 둔 `Idempotency-Key`로만 보내므로 다시 보내도 한 번만 취소된다.
   - 성공하고 취소 금액이 결제 금액과 같으면 환불 `DONE`, 결제 `CANCELED`.
   - `ALREADY_CANCELED_PAYMENT`도 완료로 본다.
   - 응답 없음·5xx·409, 그리고 토스가 잠시 후 다시 시도하라는 4xx(`PROVIDER_ERROR`·`FORBIDDEN_CONSECUTIVE_REQUEST`)는 다음 주기에 다시 보내고, 시도가 5번에 닿으면 `FAILED`.
   - 그 밖의 거절(`NOT_CANCELABLE_PAYMENT` 등)과 취소 금액 불일치는 바로 `FAILED`.

설정은 `setty.payment.recovery.*`(`PAYMENT_RECOVERY_INTERVAL`, `PAYMENT_RECOVERY_STALE_AFTER`, `PAYMENT_RECOVERY_BATCH_SIZE`, `PAYMENT_REFUND_MAX_ATTEMPTS`)이다.

## 수동 확인

환불이 `FAILED`로 멈추면 `토스 결제 취소에 실패해 환불을 멈췄습니다` 에러 로그가 남는다. 아래 조회로 대상을 찾고, 토스 상점관리자에서 결제 상태를 확인해 직접 취소한다. 직접 취소한 뒤에는 행을 `REQUESTED`로 되돌리면 다음 배치가 `ALREADY_CANCELED_PAYMENT`를 받아 완료로 기록한다.

```sql
-- 자동 환불이 멈춘 건
SELECT r.id AS refund_id, p.order_id, p.payment_key, p.amount, r.reason, r.attempts, r.last_error_code, r.completed_at
FROM payment_refunds r
JOIN payments p ON p.id = r.payment_id
WHERE r.status = 'FAILED';

-- 끝난 주문에 승인 결제가 있는데 환불 행이 없는 건(이벤트 처리 실패 등)
SELECT o.id AS order_id, o.order_status, p.id AS payment_id, p.payment_key, p.amount, p.approved_at
FROM orders o
JOIN payments p ON p.order_id = o.id
LEFT JOIN payment_refunds r ON r.payment_id = p.id
WHERE o.order_status IN ('EXPIRED', 'CANCELLED') AND p.status = 'DONE' AND r.id IS NULL;

-- 한 시간 넘게 결과를 모르는 승인 시도
SELECT order_id, payment_key, amount, attempted_at
FROM payments
WHERE status = 'UNKNOWN' AND attempted_at < NOW(6) - INTERVAL 1 HOUR;
```

두 번째 조회의 `CANCELLED`에는 구매자 취소로 끝난 주문도 섞인다. 구매자 취소 환불은 아직 자동화하지 않았으므로 이 조회로 함께 확인한다.
