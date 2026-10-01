# PaymentFailed

## 발생

- 발행자: [`PaymentService#fail`](../../src/main/java/setty/payment/application/PaymentService.java)
- 상황: 결제 실패·취소 복귀를 처리할 때 발행한다. 이미 결제가 `DONE`이면 발행하지 않는다. 실패 결제 기록은 저장하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentFailed.java)의 주문 ID.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`OrderEventListener#onPaymentFailed`](../../src/main/java/setty/platform/order/controller/OrderEventListener.java) | `PENDING` 주문을 삭제하고 매물 선점을 해제한다. 주문이 없거나 `PENDING`이 아니면 건너뛴다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.

## 확인 필요

- [결제 정책](../../../docs/policy/payment.md#승인재시도)은 결제창 취소·명확한 승인 실패 후에도 주문을 원래 만료 시각까지 `PENDING`으로 유지한다. 현재 수신 코드는 `PENDING` 주문을 삭제한다.
