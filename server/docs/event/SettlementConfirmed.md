# SettlementConfirmed

## 발생

- 발행자: [`SettlementService#apply`](../../src/main/java/setty/settlement/application/SettlementService.java). 두 경로에서 부른다.
  - `decide`: 주문의 정산 결론(판매 완료)이 도착해 이미 기록된 `PENDING` 정산을 `CONFIRMED`로 바꿀 때.
  - `record`: 결론이 먼저 와 있던 주문에 판매자 정산을 기록하면서 바로 `CONFIRMED`로 만들 때.
- 상황: 판매자(`SELLER`) 정산이 이번 처리에서 `PENDING`에서 `CONFIRMED`로 바뀐 경우에만 발행한다. 기사 정산, 취소 결론, 이미 결론이 난 행에는 발행하지 않는다. 같은 판매 완료 이벤트가 다시 와도 결론이 이미 있어 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/SettlementConfirmed.java)의 주문 ID, 매물 ID, 판매자 ID, 정산 금액, 확정 시각. 확정 시각은 판매 완료 시각이다.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderNotificationDispatcher#onSettlementConfirmed`](../../src/main/java/setty/notification/order/application/OrderNotificationDispatcher.java) | 커밋 뒤 비동기로 판매자에게 `SETTLEMENT_CONFIRMED` 종류의 `order_notifications` 행을 금액과 함께 만들고 바로 푸시한다. 확정 시각을 `occurred_at`으로 써서 재발행돼도 행은 하나다. 본문의 매물 제목은 매물 모듈의 `NotificationListingReader`로 읽고, 매물이 없으면 금액만 보낸다. 발송 실패 처리는 `OrderStatusNotified`와 같다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
