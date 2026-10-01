# OrderConfirmed

## 발생

- 발행자: [`OrderService#publishOrderConfirmed`](../../src/main/java/setty/platform/order/service/OrderService.java)
- 상황: `OrderEventListener#onPaymentCompleted`가 `PaymentCompleted`를 수신한 뒤, 주문이 `PENDING`에서 `CONFIRMED`로 처음 전이될 때 발행한다. 주문의 배송 상태도 `PENDING`에서 `REQUESTED`로 바뀐다. 이미 `CONFIRMED`인 주문에는 다시 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderConfirmed.java)에 정의된 주문 ID, 매물명·카테고리·배송비, 출발지·도착지 주소와 연락처.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`DeliveryEventListener#handle(OrderConfirmed)`](../../src/main/java/setty/delivery/event/DeliveryEventListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 `REQUESTED` 배송 요청을 생성한다. 같은 주문의 요청이 이미 있으면 건너뛴다. 새 요청을 만들면 `DeliveryRequestsChanged`를 발행한다. 처리 실패는 로그에 기록한다. |

## 다음 확인할 이벤트 문서

- [DeliveryRequestsChanged.md](DeliveryRequestsChanged.md): 새 배송 요청을 생성했을 때 발행한다. 기존 요청을 건너뛰면 발행하지 않는다.

## 확인 필요

- [배송 정책](../../../docs/policy/delivery.md#요청정보-확정)은 주문 시점에 저장한 주소·연락처를 배송 요청에 사용하도록 정의한다. 현재 발행 코드는 이벤트 생성 시 회원의 주소·연락처를 조회한다. 두 시점 사이 회원정보가 바뀔 때 사용할 값을 정해야 한다.
