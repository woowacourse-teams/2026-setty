# DeliveryCancellationRejected

## 발생

- 발행자: [`DeliveryLifecycleService#cancel`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 주문 취소 요청을 처리할 때 배송이 `ACCEPTED`·`PICKED_UP`·`DELIVERED`여서 취소할 수 없으면 발행한다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DeliveryCancellationRejected.java)의 배송 ID, 주문 ID, 취소 요청 ID, 판단 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onDeliveryCancellationRejected`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | 취소 요청 ID가 일치하는 `CANCEL_PENDING` 주문을 `CONFIRMED`로 되돌린다. 일치하지 않거나 이미 다른 상태면 건너뛴다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
