# DeliveryAssignmentCancelled

## 발생

- 발행자: [`DeliveryLifecycleService#cancelAssignment`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 담당 기사가 픽업 전에 `ACCEPTED` 배송의 배차를 취소해 `REQUESTED`로 되돌릴 때 발행한다. 담당 기사와 수락 시각은 비운다. 같은 처리에서 `DeliveryRequestsChanged`도 발행한다. 다른 기사의 요청이나 `ACCEPTED`가 아닌 배송에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryAssignmentCancelled.java)의 배송 ID, 주문 ID, 취소한 기사 ID, 취소한 배차의 수락 시각(`acceptedAt`), 취소 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryAssignmentCancelled`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 주문의 배송 상태가 `ACCEPTED`이고 기록된 수락 시각이 `acceptedAt`과 같을 때만 `REQUESTED`로 되돌린다. 이미 다른 기사의 수락이 반영됐거나, `PICKED_UP` 이후이거나, 같은 취소가 다시 와서 이미 `REQUESTED`인 경우(중복)는 건너뛰고 로그를 남긴다. 리스너가 비동기라 이벤트 순서가 바뀌어 도착할 수 있어 시각 크기가 아니라 같은 배차인지로 판단한다. |

매물은 수신하지 않는다. 예약(`RESERVED`)은 유지하고, 이후 구매자 취소가 확정되면 [OrderCancelled.md](OrderCancelled.md)에서 해제한다.

## 다음 확인할 이벤트 문서

- [DeliveryRequestsChanged.md](DeliveryRequestsChanged.md): `cancelAssignment` 처리에서 배차 취소 이벤트와 함께 발행한다.
