# PaymentRefundRequested

## 발생

- 발행자: [`OrderService#publishOrderConfirmed`](../../src/main/java/setty/platform/order/service/OrderService.java)
- 상황: `PaymentCompleted`를 받은 주문이 이미 `EXPIRED`이거나 판매자 취소로 `CANCELLED`일 때 발행한다. 주문 행을 잠근 트랜잭션에서 발행하므로 주문 상태와 함께 커밋되고, 주문은 되살리지 않는다. `PaymentCompleted`가 재발행되면 다시 발행될 수 있다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentRefundRequested.java)의 주문 ID와 주문 상태(`EXPIRED`·`CANCELLED`).

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`PaymentEventListener#onPaymentRefundRequested`](../../src/main/java/setty/payment/presentation/PaymentEventListener.java) | 그 주문의 승인된 결제를 `payment_refunds`에 `REQUESTED`로 기록한다. 주문 상태로 환불 사유(`ORDER_EXPIRED`·`ORDER_CANCELLED`)를 정한다. 같은 결제의 환불 행이 이미 있으면 아무것도 하지 않는다. 승인된 결제가 없으면 에러 로그만 남긴다. 기록된 환불은 결제 복구 배치가 토스 결제 취소로 처리한다([수동 확인 절차](../payment/expired-order-manual-refund.md)). |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
