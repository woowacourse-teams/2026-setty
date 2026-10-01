# DeliveryPickedUp

## 발생

- 발행자: [`DeliveryLifecycleService#pickUp`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 담당 기사가 `ACCEPTED` 배송의 인수를 등록해 `PICKED_UP`으로 전이할 때 발행한다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryPickedUp.java)의 배송 ID, 주문 ID, 인수 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryPickedUp`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 주문의 배송 상태를 `PICKED_UP`으로 동기화한다. |
