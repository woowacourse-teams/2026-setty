# PaymentCompleted

## 발생

- 발행자: [`PaymentRecorder#recordCompleted`](../../src/main/java/setty/payment/application/PaymentRecorder.java)
- 상황: 토스 승인 성공 후 결제를 `DONE`으로 저장할 때 같은 트랜잭션에서 발행한다. 이미 `DONE`인 결제의 승인 요청은 `PaymentService#confirm`에서 반환하므로 다시 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentCompleted.java)의 주문 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onPaymentCompleted`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 주문을 `PENDING`에서 `CONFIRMED`로 전이하고 `OrderConfirmed`를 발행한다. 이미 확정된 주문은 다시 발행하지 않는다. |

## 다음 확인할 이벤트 문서

- [OrderConfirmed.md](OrderConfirmed.md): 주문이 처음 `PENDING`에서 `CONFIRMED`로 전이할 때 발행한다.

## 확인 필요

- [결제 정책](../../../docs/policy/payment.md#승인재시도)은 만료·취소된 주문의 뒤늦은 승인 성공을 전액 환불 대상으로 다룬다. 현재 수신 경로는 `PENDING` 외 주문을 환불 대상으로 기록하지 않으며, `EXPIRED`·`CANCELLED` 주문에서는 상태 전이 예외가 발생한다.
