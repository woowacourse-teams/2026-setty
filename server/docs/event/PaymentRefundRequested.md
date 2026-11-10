# PaymentRefundRequested

## 발생

- 발행자: 없음. 끝난 주문의 승인은 이제 결제 모듈이 결제를 기록하는 트랜잭션에서 환불 대상으로 함께 기록한다([`PaymentApprovalRecorder`](../../src/main/java/setty/payment/application/PaymentApprovalRecorder.java)). 주문이 먼저 커밋하는 순서에서는 이 이벤트가 결제 `DONE`보다 먼저 처리돼 환불 요청이 사라질 수 있어 발행을 멈췄다.
- 상황: 이전 버전이 발행해 배포 시점에 처리되지 않고 남은 기록만 처리한다. 남은 기록이 없음을 확인한 뒤 이벤트 클래스와 수신자를 지운다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/PaymentRefundRequested.java)의 주문 ID와 주문 상태(`EXPIRED`·`CANCELLED`).

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`PaymentEventListener#onPaymentRefundRequested`](../../src/main/java/setty/payment/presentation/PaymentEventListener.java) | 그 주문의 승인된 결제를 `payment_refunds`에 `REQUESTED`로 기록한다. 주문 상태로 환불 사유(`ORDER_EXPIRED`·`ORDER_CANCELLED`)를 정한다. 같은 결제의 환불 행이 이미 있으면 아무것도 하지 않는다. 승인된 결제가 없으면 에러 로그만 남긴다. 기록된 환불은 결제 복구 배치가 토스 결제 취소로 처리한다([수동 확인 절차](../payment/expired-order-manual-refund.md)). |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
