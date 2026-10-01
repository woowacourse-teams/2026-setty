# OrderCancelled

## 발생

- 발행자: [`OrderService#confirmCancellation`](../../src/main/java/setty/platform/order/service/OrderService.java)
- 상황: `DeliveryCancelled`를 받은 주문이 현재 취소 요청 ID와 일치하고 `CANCEL_PENDING`에서 `CANCELLED`로 전이할 때 발행한다. 중복되거나 오래된 취소 결과에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderCancelled.java)의 주문 ID, 매물 ID, 취소 요청 ID, 취소 확정 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`ListingDeliveryEventListener#onOrderCancelled`](../../src/main/java/setty/platform/listing/application/ListingDeliveryEventListener.java) | 해당 매물의 구매 선점을 해제한다. |

## 확인 필요

- [결제 정책](../../../docs/policy/payment.md#환불)은 주문 취소 확정 이벤트를 받은 결제 영역이 승인 결제를 환불 대상으로 기록하도록 정의한다. 현재 `OrderCancelled`의 직접 수신자는 매물 영역뿐이다.
