# PaymentCompleted

## 발생

- 발행자: [`PaymentRecorder#recordCompleted`](../../src/main/java/setty/payment/application/PaymentRecorder.java)
- 상황: 토스 승인 성공 후 결제를 `DONE`으로 저장할 때 같은 트랜잭션에서 발행한다. 이미 `DONE`인 결제의 승인 요청은 `PaymentService#confirm`에서 반환하므로 다시 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentCompleted.java)의 주문 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onPaymentCompleted`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | `PENDING` 주문을 `CONFIRMED`로 전이하고 `OrderConfirmed`를 발행한다. `EXPIRED` 주문은 유지하고 수동 환불 확인 로그를 남긴다. 이미 확정된 주문은 다시 발행하지 않는다. |

## 다음 확인할 이벤트 문서

- [OrderConfirmed.md](OrderConfirmed.md): 주문이 처음 `PENDING`에서 `CONFIRMED`로 전이할 때 발행한다.

## 확인 필요

- [결제 정책](../../../docs/policy/payment.md#승인재시도)의 자동 환불 대상 기록·환불 실행은 아직 구현되지 않았다. `EXPIRED` 주문의 `DONE` 결제는 [수동 확인 절차](../payment/expired-order-manual-refund.md)에 따라 판단한다. `CANCELLED` 주문의 뒤늦은 승인은 이번 변경 범위 밖이다.
