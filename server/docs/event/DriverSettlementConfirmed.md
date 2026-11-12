# DriverSettlementConfirmed

## 발생

- 발행자: [`SettlementService#apply`](../../src/main/java/setty/settlement/application/SettlementService.java). [`SettlementConfirmed`](SettlementConfirmed.md)와 같은 두 경로에서 부른다.
  - `decide`: 주문의 정산 결론(판매 완료)이 도착해 이미 기록된 `PENDING` 기사 정산을 `CONFIRMED`로 바꿀 때.
  - `record`: 결론이 먼저 와 있던 주문에 기사 정산(배송 완료 때 기록)을 기록하면서 바로 `CONFIRMED`로 만들 때.
- 상황: 기사(`DRIVER`) 정산이 이번 처리에서 `PENDING`에서 `CONFIRMED`로 바뀐 경우에만 발행한다. 판매자 정산은 `SettlementConfirmed`로 따로 낸다. 취소 결론, 이미 결론이 난 행에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/DriverSettlementConfirmed.java)의 주문 ID, 기사 ID, 정산 금액(배송비), 확정 시각. 기사 정산에는 매물이 없어 매물 ID가 없다.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderNotificationDispatcher#onDriverSettlementConfirmed`](../../src/main/java/setty/notification/order/application/OrderNotificationDispatcher.java) | 커밋 뒤 비동기로 기사에게 `DRIVER_SETTLEMENT_CONFIRMED` 종류의 `order_notifications` 행(`owner_type = DRIVER`, `listing_id` 없음)을 금액과 함께 만들고 기사 기기로 푸시한다. 본문은 "{금액}원"이다. 확정 시각을 `occurred_at`으로 써서 재발행돼도 행은 하나다. 발송 실패 처리는 `OrderStatusNotified`와 같다. 기사는 `/api/delivery/notifications`로 알림함을 본다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
