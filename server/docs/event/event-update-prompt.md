# 이벤트 문서 갱신 프롬프트

이 문서는 이벤트 관련 코드 변경을 이벤트 문서에 반영할 때 작업 지시로 사용한다. 이벤트별 작성 기준은 [이벤트 문서 작성 가이드](event-guide.md)를 따른다.

## 작업 프롬프트

```text
현재 코드 변경을 기준으로 애플리케이션 이벤트 문서를 갱신하라.

1. 변경된 이벤트 클래스와 발행자·수신자를 코드와 테스트에서 찾고, 발행 조건과 수신 시점을 확인한다.
2. 변경된 이벤트의 문서에서 발생 조건, 전달 값, Fanout, 다음 확인할 이벤트 문서를 갱신한다.
3. 이벤트 문서의 다음 확인 링크를 따라가며 후속 이벤트 문서와 그 후속 문서를 차례로 검토한다. 코드 동작이 달라진 문서만 갱신한다.
4. 발행 관계가 추가, 삭제, 조건 변경되면 해당 이벤트 파일의 다음 확인 링크를 갱신한다. 이벤트 문서가 추가·삭제된 경우에만 이 문서의 인덱스를 갱신한다.
5. 이 문서의 이벤트 문서 인덱스가 실제 발행 이벤트 문서 전체와 일치하는지 확인한다.
6. 상대 링크와 diff를 검증하고, 근거 없는 동작은 단정하지 말고 확인 필요로 남긴다.
```

## 이벤트 문서 인덱스

- 배송: [DeliveryAccepted.md](DeliveryAccepted.md), [DeliveryCancellationRejected.md](DeliveryCancellationRejected.md), [DeliveryCancelled.md](DeliveryCancelled.md), [DeliveryDelivered.md](DeliveryDelivered.md), [DeliveryPickedUp.md](DeliveryPickedUp.md), [DeliveryRequestsChanged.md](DeliveryRequestsChanged.md)
- 매물: [ListingCreated.md](ListingCreated.md)
- 주문: [OrderCancelled.md](OrderCancelled.md), [OrderCancellationRequested.md](OrderCancellationRequested.md), [OrderCompleted.md](OrderCompleted.md), [OrderConfirmed.md](OrderConfirmed.md)
- 결제: [PaymentCompleted.md](PaymentCompleted.md), [PaymentFailed.md](PaymentFailed.md)

인덱스는 현재 발행 이벤트 문서의 파일 목록만 제공한다. 후속 발행 관계의 단일 출처는 각 이벤트 파일의 `다음 확인할 이벤트 문서` 절이다.
