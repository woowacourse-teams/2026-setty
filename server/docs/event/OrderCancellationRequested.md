# OrderCancellationRequested

## 발생

- 발행자: [`OrderService#requestCancellation`](../../src/main/java/setty/platform/order/service/OrderService.java)
- 상황: 구매자 취소 요청으로 주문이 `CONFIRMED`에서 `CANCEL_PENDING`으로 처음 전이될 때 발행한다. 이미 `CANCEL_PENDING`인 주문의 재요청에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderCancellationRequested.java)의 주문 ID와 취소 요청 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`DeliveryEventListener#handle(OrderCancellationRequested)`](../../src/main/java/setty/delivery/event/DeliveryEventListener.java) | 발행 트랜잭션 커밋 후 배송 상태로 취소 가능 여부를 판단한다. 배송 요청이 없거나 `REQUESTED`·`CANCELLED`이면 `DeliveryCancelled`, 그 이후 상태면 `DeliveryCancellationRejected`를 발행한다. `REQUESTED`를 취소하면 `DeliveryRequestsChanged`도 발행한다. 처리 실패는 로그에 기록한다. |
