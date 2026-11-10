# OrderPaid

## 발생

- 발행자: [`OrderPaymentAcceptor#accept`](../../src/main/java/setty/platform/order/service/OrderPaymentAcceptor.java)
- 상황: 결제 모듈이 토스 승인을 확인한 뒤 주문 경계(`PaymentOrderAcceptance`)를 부르면, 만료 시각 전의 `PENDING` 주문을 `PAID`로 바꾸는 트랜잭션에서 발행한다. 이 트랜잭션은 결제 기록보다 먼저 커밋된다. 이미 `PAID` 이후인 주문에 같은 승인이 다시 오면 발행하지 않는다. 주문 모듈 안에서만 쓰는 이벤트다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/platform/order/service/OrderPaid.java)의 주문 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderPaidListener#onOrderPaid`](../../src/main/java/setty/platform/order/service/OrderPaidListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 `PAID` 주문을 `CONFIRMED`로 바꾸고 배송 상태를 `REQUESTED`로 바꾼 뒤 `OrderConfirmed`와 [`OrderStatusNotified`](OrderStatusNotified.md)(`CONFIRMED`)를 발행한다. 이미 확정 이후면 아무것도 하지 않는다. 실패해도 결제 `DONE`과 주문 `PAID`는 남고, 재발행이 끝난 뒤에도 남은 `PAID`는 [`PaidOrderConfirmationScheduler`](../../src/main/java/setty/platform/order/service/PaidOrderConfirmationScheduler.java)가 결제 만료 시각에서 15분이 지난 뒤 다시 확정한다. |

## 다음 확인할 이벤트 문서

- [OrderConfirmed.md](OrderConfirmed.md): 주문이 `PAID`에서 `CONFIRMED`로 처음 전이할 때 발행한다.
- [OrderStatusNotified.md](OrderStatusNotified.md): `OrderConfirmed`와 함께 발행한다.
