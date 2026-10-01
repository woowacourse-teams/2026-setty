# 이벤트 문서 영향 매핑

이 문서는 이벤트 처리 흐름에서 다른 이벤트를 발행하는 관계를 기록한다. 이벤트 코드가 바뀌면 변경된 이벤트 문서를 먼저 갱신한 뒤, 이 표의 후속 이벤트 문서를 따라가며 영향을 검토한다.

## 갱신 순서

1. 변경한 이벤트의 발생 조건, 필드, Fanout을 해당 이벤트 문서에 반영한다.
2. 아래 표에서 해당 이벤트의 후속 발행 경로를 확인하고, 영향이 있는 이벤트 문서도 갱신한다. 표의 문서는 검토 대상이며, 실제 계약·조건·Fanout에 변화가 있을 때 내용을 수정한다.
3. 후속 이벤트의 처리 결과가 다시 이벤트를 발행하면 그 경로를 계속 확인한다.
4. 이벤트 발행·수신 관계가 추가, 삭제 또는 변경되면 이 매핑도 갱신한다.

## 후속 발행 경로

| 기준 이벤트 문서 | 후속 이벤트 문서 | 발행 경로와 조건 |
| --- | --- | --- |
| [PaymentCompleted](PaymentCompleted.md) | [OrderConfirmed](OrderConfirmed.md) | `OrderEventListener#onPaymentCompleted`가 결제 완료 이벤트를 받고, 주문이 처음 `PENDING`에서 `CONFIRMED`로 전이하면 발행한다. |
| [OrderConfirmed](OrderConfirmed.md) | [DeliveryRequestsChanged](DeliveryRequestsChanged.md) | `DeliveryEventListener#handle(OrderConfirmed)`가 새 배송 요청을 등록할 때 목록 변경 이벤트를 발행한다. 같은 주문의 배송 요청이 이미 있으면 후속 발행하지 않는다. |
| [OrderCancellationRequested](OrderCancellationRequested.md) | [DeliveryCancelled](DeliveryCancelled.md), [DeliveryCancellationRejected](DeliveryCancellationRejected.md) | `DeliveryEventListener#handle(OrderCancellationRequested)`가 배송 상태로 취소 가능 여부를 판단한다. 취소 성공 경로는 `DeliveryCancelled`, 취소 불가 경로는 `DeliveryCancellationRejected`를 발행한다. |
| [OrderCancellationRequested](OrderCancellationRequested.md) | [DeliveryRequestsChanged](DeliveryRequestsChanged.md) | 취소 요청 처리 중 `REQUESTED` 배송을 `CANCELLED`로 전이할 때 목록 변경 이벤트를 함께 발행한다. |
| [DeliveryCancelled](DeliveryCancelled.md) | [OrderCancelled](OrderCancelled.md) | `OrderEventListener#onDeliveryCancelled`가 현재 취소 요청과 일치하는 `CANCEL_PENDING` 주문을 취소 확정할 때 발행한다. |
| [DeliveryAccepted](DeliveryAccepted.md) | [DeliveryRequestsChanged](DeliveryRequestsChanged.md) | `DeliveryLifecycleService#accept`가 배송 수락 이벤트와 목록 변경 이벤트를 같은 처리에서 발행한다. |
| [DeliveryCancelled](DeliveryCancelled.md) | [DeliveryRequestsChanged](DeliveryRequestsChanged.md) | `DeliveryLifecycleService#cancel`이 `REQUESTED` 배송을 `CANCELLED`로 전이할 때 목록 변경 이벤트를 함께 발행한다. 배송이 없거나 이미 취소된 경로에서는 발행하지 않는다. |

이 매핑은 현재 코드에서 확인한 경로다. 새로운 이벤트 발행 경로가 추가되면 해당 이벤트 문서를 작성하고 표에 연결을 추가한다.
