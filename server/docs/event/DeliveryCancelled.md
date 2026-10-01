# DeliveryCancelled

## 발생

- 발행자: [`DeliveryLifecycleService#cancel`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 주문 취소 요청을 처리할 때 배송 요청이 없거나 이미 `CANCELLED`이면 발행한다. `REQUESTED` 배송을 `CANCELLED`로 전이한 경우에도 발행하고 `DeliveryRequestsChanged`를 추가 발행한다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryCancelled.java)의 배송 ID, 주문 ID, 취소 요청 ID, 판단 시각. 배송 요청이 없으면 배송 ID는 `null`이다.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryCancelled`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 취소 요청 ID가 일치하는 `CANCEL_PENDING` 주문을 `CANCELLED`로 확정하고 `OrderCancelled`를 발행한다. 중복되거나 오래된 결과는 건너뛴다. |

## 다음 확인할 이벤트 문서

- [OrderCancelled.md](OrderCancelled.md): 취소 요청 ID가 일치하는 `CANCEL_PENDING` 주문을 취소 확정할 때 발행한다.
- [DeliveryRequestsChanged.md](DeliveryRequestsChanged.md): `REQUESTED` 배송을 취소하는 처리에서 함께 발행한다.
