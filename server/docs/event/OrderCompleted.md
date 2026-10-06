# OrderCompleted

## 발생

- 발행자: [`OrderCompletionService#confirmByBuyer`](../../src/main/java/setty/platform/order/service/OrderCompletionService.java), [`OrderCompletionService#completeIfDue`](../../src/main/java/setty/platform/order/service/OrderCompletionService.java)
- 상황: 배송 완료(`DELIVERED`)된 `CONFIRMED` 주문이 `COMPLETED`로 처음 전이될 때 발행한다. 구매자가 `POST /api/orders/{id}/completion`으로 확인하거나, `OrderCompletionScheduler`가 배송 완료 후 3일이 지난 주문을 찾아 확정한다. 이미 `COMPLETED`인 주문에는 다시 발행하지 않는다. 취소 대기 중(`CANCEL_PENDING`) 주문은 자동 확정하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderCompleted.java)의 주문 ID, 판매 완료 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`ListingDeliveryEventListener#onOrderCompleted`](../../src/main/java/setty/platform/listing/application/ListingDeliveryEventListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 주문에 연결된 매물을 판매 완료(`SOLD`)로 전이한다. 이미 `SOLD`이면 건너뛴다. |
| [`SettlementEventListener#handle`](../../src/main/java/setty/settlement/event/SettlementEventListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 주문의 정산 결론을 판매 완료로 기록하고 `PENDING` 정산을 `CONFIRMED`로 바꾼다. 이후 기록되는 정산도 바로 확정한다. 이미 결론 난 주문이면 건너뛴다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
