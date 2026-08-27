# common/ — 팀 간 계약 영역

이 파일은 `server/src/main/java/setty/common/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`가 원본이다.

## 소유권

- 플랫폼 팀·배송 팀 **양 팀의 계약 영역**이다. 합의 없이 수정하지 않는다.
- 변경이 필요하면 코드를 고치지 말고 "common 변경은 양 팀 합의가 필요합니다"라고 사용자에게 알린다.

## 내용물 (계약)

- `DeliveryStatus` — 배송 상태 enum. REQUESTED → ACCEPTED → PICKED_UP → DELIVERED 순서로만 전이한다.
- `OrderRequested` — 주문 생성 시 플랫폼 팀이 발행하고 배송 팀이 수신하는 이벤트 (orderId, itemName, category, pickupAddress, deliveryAddress, deliveryFee, pickupPhoneNumber, deliveryPhoneNumber).
- `DeliveryStatusChanged` — 배송 상태 변경 시 배송 팀이 발행하고 플랫폼 팀이 수신하는 이벤트 (deliveryId, orderId, status, changedAt). 플랫폼은 이걸 받아 `orders.delivery_status`를 갱신한다 (#236, DEC-13).

## 금지사항

- 팀별 비즈니스 로직을 이 패키지에 넣지 않는다. 상태 정의·이벤트 같은 팀 간 계약만 둔다.
- 계약 타입의 필드·전이 순서를 한쪽 팀 판단으로 바꾸지 않는다.
