# OrderStatusNotified

## 발생

- 발행자: [`OrderStatusNotifier#notify`](../../src/main/java/setty/platform/order/service/OrderStatusNotifier.java). 아래 처리에서 상태가 실제로 바뀐 경우에만 부른다. 같은 원인 이벤트가 다시 와서 상태가 그대로면 발행하지 않는다.
  - [`OrderService#confirmPaid`](../../src/main/java/setty/platform/order/service/OrderService.java): 승인을 받아들인(`PAID`) 주문이 `CONFIRMED`가 될 때 `CONFIRMED`.
  - [`OrderService#confirmCancellation`](../../src/main/java/setty/platform/order/service/OrderService.java): 구매자 취소가 확정될 때 `CANCELLED_BY_BUYER`.
  - [`OrderService#rejectCancellation`](../../src/main/java/setty/platform/order/service/OrderService.java): 취소가 거절돼 `CONFIRMED`로 돌아올 때 `CANCELLATION_REJECTED`.
  - [`SyncOrderDeliveryStatusService#sync`](../../src/main/java/setty/platform/order/service/SyncOrderDeliveryStatusService.java): 배송 상태가 `ACCEPTED`·`PICKED_UP`·`DELIVERED`로 바뀔 때. 같은 `ACCEPTED`라도 수락 시각이 다르면(재배차) 다시 발행한다.
  - [`SyncOrderDeliveryStatusService#reopen`](../../src/main/java/setty/platform/order/service/SyncOrderDeliveryStatusService.java): 기사의 배차 취소로 배송 상태가 `ACCEPTED`에서 `REQUESTED`로 돌아갈 때 `DELIVERY_ASSIGNMENT_CANCELLED`. 이미 다른 배차가 반영돼 되돌리지 않은 경우(늦게 도착한 취소, 중복)는 발행하지 않는다.
  - [`OrderCompletionService`](../../src/main/java/setty/platform/order/service/OrderCompletionService.java): 구매자 확인이나 자동 확정으로 `COMPLETED`가 될 때 `COMPLETED`.
  - [`SellerOrderCancellation#cancelPendingOrdersOf`](../../src/main/java/setty/platform/order/service/SellerOrderCancellation.java): 판매자가 결제 대기 주문이 걸린 매물을 지워 주문이 취소될 때 `CANCELLED_BY_SELLER`.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderStatusNotified.java)의 주문 ID, 매물 ID, 구매자 ID, 판매자 ID, 매물 제목, 바뀐 내용, 바뀐 시각. 바뀐 시각은 배송·취소 결과는 원래 이벤트 시각, 판매 완료는 완료 시각, 결제 확정·판매자 취소는 처리 시각이다.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderNotificationDispatcher#onOrderStatusNotified`](../../src/main/java/setty/notification/order/application/OrderNotificationDispatcher.java) | 커밋 뒤 비동기로 받는 사람마다 `order_notifications` 행을 만들고 바로 푸시한다. `DELIVERY_ASSIGNMENT_CANCELLED`는 구매자·판매자 모두에게 "기사가 배차를 취소해 다시 기사를 찾고 있어요"로 간다. `COMPLETED`는 구매자에게만 만들고, 판매자에게는 `SettlementConfirmed`로 정산 확정 알림이 따로 간다(`SALE_COMPLETED`는 더 만들지 않는다). `(주문, 받는 사람 종류, 받는 사람, 종류, 바뀐 시각)` 유니크 키로 재발행돼도 행은 하나다. 발송 실패는 시도 횟수를 올리고 세 번째에 `FAILED`가 된다. 재시도할 알림이 남으면 예외를 던져 발행 기록을 남긴다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
