# OrderConfirmed

## 발생

- 발행자: [`OrderService#publishOrderConfirmed`](../../src/main/java/setty/platform/order/service/OrderService.java)
- 상황: `OrderEventListener#onPaymentCompleted`가 `PaymentCompleted`를 수신한 뒤, 주문이 `PENDING`에서 `CONFIRMED`로 처음 전이될 때 발행한다. 주문의 배송 상태도 `PENDING`에서 `REQUESTED`로 바뀐다. 이미 `CONFIRMED`인 주문에는 다시 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/OrderConfirmed.java)에 정의된 주문 ID, 매물명·카테고리·배송비, 출발지·도착지 주소와 연락처, 정산에 쓰는 매물 ID·판매자 ID·물품 가격, 도착지 행정동 코드.
- 배송비·도착지 주소·연락처·행정동은 주문을 만들 때 `orders`에 저장한 값이다. 배송비는 구매자 행정동 기준 거리 배송비다. 저장값이 없는 도입 전 주문은 매물 배송비와 발행 시점의 회원 주소·연락처로 대신하고, 행정동은 `null`이다.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`DeliveryEventListener#handle(OrderConfirmed)`](../../src/main/java/setty/delivery/event/DeliveryEventListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 `REQUESTED` 배송 요청을 생성한다. 같은 주문의 요청이 이미 있으면 건너뛴다. 새 요청을 만들면 `DeliveryRequestsChanged`를 발행한다. 처리 실패는 로그에 기록한다. |
| [`SettlementEventListener#handle`](../../src/main/java/setty/settlement/event/SettlementEventListener.java) | 발행 트랜잭션 커밋 후 별도 트랜잭션에서 판매자 정산(물품 가격)을 `PENDING`으로 기록한다. 같은 주문의 판매자 정산이 있으면 건너뛰고, 주문의 정산 결론(판매 완료·취소)이 먼저 와 있으면 바로 적용한다. |

## 다음 확인할 이벤트 문서

- [DeliveryRequestsChanged.md](DeliveryRequestsChanged.md): 새 배송 요청을 생성했을 때 발행한다. 기존 요청을 건너뛰면 발행하지 않는다.

## 확인 필요

- 출발지(판매자) 주소·연락처는 아직 발행 시점의 회원 정보를 조회한다. [배송 정책](../../../docs/policy/delivery.md#요청정보-확정)은 배송지(도착지)만 주문 시점 저장을 정의한다.
