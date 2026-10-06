# OrderCompleted

## 발생

- 발행자: 현재 발행하는 코드 없음.
- 상황: [판매 완료·정산 정책](../../../docs/policy/completion-settlement.md#완료-및-정산-확정)에 따라 구매자가 배송 완료를 확인하거나 배송 완료 후 3일이 지나 판매 완료가 확정될 때 발행할 예정이다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderCompleted.java)의 주문 ID, 판매 완료 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`SettlementEventListener#handle`](../../src/main/java/setty/settlement/event/SettlementEventListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 주문의 정산 결론을 판매 완료로 기록하고 `PENDING` 정산을 `CONFIRMED`로 바꾼다. 이후 기록되는 정산도 바로 확정한다. 이미 결론 난 주문이면 건너뛴다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.

## 확인 필요

- 구매자 확인 API와 3일 자동 확정이 아직 없어 이 이벤트를 발행하지 않는다. 그동안 정산은 `PENDING`에 머문다.
