# PaymentFailed

## 발생

- 발행자: [`PaymentService#fail`](../../src/main/java/setty/payment/application/PaymentService.java)
- 상황: 결제 실패·취소 복귀를 처리할 때 발행한다. 이미 결제가 `DONE`이면 발행하지 않는다. 실패 결제 기록은 저장하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentFailed.java)의 주문 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| 현재 직접 수신자 없음 | 주문은 원래 만료 시각까지 `PENDING`을 유지한다. 만료 스케줄러가 이후 `EXPIRED`로 전이하고 매물 선점을 해제한다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
