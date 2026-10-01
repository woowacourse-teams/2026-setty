# DeliveryRequestsChanged

## 발생

- 발행자: [`RegisterDeliveryService#register`](../../src/main/java/setty/delivery/application/RegisterDeliveryService.java), [`DeliveryLifecycleService#accept`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java), [`DeliveryLifecycleService#cancel`](../../src/main/java/setty/delivery/application/DeliveryLifecycleService.java)
- 상황: 새 배송 요청이 생성되거나, 요청이 기사에게 수락되거나, `REQUESTED` 요청이 취소되어 기사에게 보이는 목록이 바뀔 때 발행한다. 기존 요청을 재등록하거나 이미 취소된 요청을 다시 처리할 때는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/delivery/application/DeliveryRequestsChanged.java)에 필드가 없다. 변경된 목록은 수신자가 다시 조회한다.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`DeliveryRequestEventStream#on`](../../src/main/java/setty/delivery/api/DeliveryRequestEventStream.java) | 발행 트랜잭션 커밋 후 연결된 기사 SSE 구독자에게 `delivery-requests-changed` 이벤트를 보낸다. 데이터는 `{}`이며 구독자가 목록을 다시 조회한다. 연결 전송 오류는 해당 구독자를 제거하고, 그 밖의 처리 오류는 로그에 기록한다. |
