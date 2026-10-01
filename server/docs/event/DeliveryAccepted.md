# DeliveryAccepted

## 발생

- 발행자: [`DeliveryLifecycleService#accept`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 기사가 `REQUESTED` 배송을 수락해 `ACCEPTED`로 전이할 때 발행한다. 같은 처리에서 `DeliveryRequestsChanged`도 발행한다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryAccepted.java)의 배송 ID, 주문 ID, 수락 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryAccepted`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 주문의 배송 상태를 `ACCEPTED`로 동기화한다. |
| [`ListingDeliveryEventListener#onDeliveryAccepted`](../../src/main/java/setty/platform/listing/application/ListingDeliveryEventListener.java) | 주문에 연결된 매물을 배송 예약 상태로 전이한다. |

## 다음 확인할 이벤트 문서

- [DeliveryRequestsChanged.md](DeliveryRequestsChanged.md): `accept` 처리에서 배송 수락 이벤트와 함께 발행한다.
