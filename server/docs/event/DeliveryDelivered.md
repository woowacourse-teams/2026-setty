# DeliveryDelivered

## 발생

- 발행자: [`DeliveryLifecycleService#complete`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 담당 기사가 `PICKED_UP` 배송의 완료를 등록해 `DELIVERED`로 전이할 때 발행한다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryDelivered.java)의 배송 ID, 주문 ID, 배송 완료 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryDelivered`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 주문의 배송 상태를 `DELIVERED`로 동기화한다. |
| [`ListingDeliveryEventListener#onDeliveryDelivered`](../../src/main/java/setty/platform/listing/application/ListingDeliveryEventListener.java) | 주문에 연결된 매물을 즉시 판매 완료(`SOLD`)로 전이한다. |

## 확인 필요

- [판매 완료·정산 정책](../../../docs/policy/completion-settlement.md#완료-및-정산-확정)은 배송 완료만으로 판매 완료를 확정하지 않고 구매자 확인 또는 3일 경과를 기다린다. 현재 매물 수신자는 `DeliveryDelivered`를 받으면 즉시 판매 완료한다.
