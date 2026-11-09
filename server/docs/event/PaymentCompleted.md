# PaymentCompleted

## 발생

- 발행자: [`PaymentRecorder#recordCompleted`](../../src/main/java/setty/payment/application/PaymentRecorder.java)
- 상황: 토스 승인이 확인되어 결제를 `DONE`으로 저장할 때 같은 트랜잭션에서 발행한다. 승인 요청 응답으로 확인한 경우와, 결과를 모르던 시도(`UNKNOWN`)를 결제 복구 배치(`UnknownPaymentResolver`)가 토스 조회로 확인한 경우가 있다. 결제 행을 잠근 뒤 이미 같은 결제 키로 승인된 행이면 다시 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentCompleted.java)의 주문 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onPaymentCompleted`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | `PENDING` 주문을 `CONFIRMED`로 전이하고 `OrderConfirmed`와 [`OrderStatusNotified`](OrderStatusNotified.md)(`CONFIRMED`)를 발행한다. `EXPIRED`·`CANCELLED`(판매자 취소) 주문은 유지하고 [`PaymentRefundRequested`](PaymentRefundRequested.md)를 발행한다. 이미 확정된 주문은 다시 발행하지 않는다. |

## 다음 확인할 이벤트 문서

- [OrderConfirmed.md](OrderConfirmed.md): 주문이 처음 `PENDING`에서 `CONFIRMED`로 전이할 때 발행한다.
- [OrderStatusNotified.md](OrderStatusNotified.md): `OrderConfirmed`와 함께 발행한다.
- [PaymentRefundRequested.md](PaymentRefundRequested.md): 주문이 이미 만료·판매자 취소됐을 때 발행한다.
