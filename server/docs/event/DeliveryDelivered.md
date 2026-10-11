# DeliveryDelivered

## 발생

- 발행자: [`DeliveryLifecycleService#complete`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 담당 기사가 `PICKED_UP` 배송의 완료를 등록해 `DELIVERED`로 전이할 때 발행한다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryDelivered.java)의 배송 ID, 주문 ID, 배송 완료 시각, 정산에 쓰는 담당 기사 ID와 배송비.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryDelivered`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 주문의 배송 상태를 `DELIVERED`로, 배송 상태 변경 시각을 배송 완료 시각으로 동기화한다. 판매 완료는 구매자 확인 또는 배송 완료 후 3일이 지난 뒤 [`OrderCompleted`](OrderCompleted.md)로 확정한다. |
| [`SettlementEventListener#handle`](../../src/main/java/setty/settlement/event/SettlementEventListener.java) | 기사 정산(배송비)을 `PENDING`으로 기록한다. 같은 주문의 기사 정산이 있으면 건너뛰고, 주문의 정산 결론(판매 완료·취소)이 먼저 와 있으면 바로 적용한다. 배송 완료만으로 정산을 확정하지 않는다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음. 판매 완료는 별도 흐름에서 [OrderCompleted.md](OrderCompleted.md)로 이어진다.

