# ADR-0004: Order 배송 상태를 플랫폼 JPA 경로로만 갱신

## 상태

**채택**

[ADR-0002](0002-synchronous-order-status-sync.md)와 [ADR-0003](0003-temporary-jdbc-order-status-access.md)을 대체한다. 동기 이벤트와 단일 트랜잭션 결정은 유지한다.

## 맥락

[#236](https://github.com/woowacourse-teams/2026-setty/issues/236)에서 주문 상태 갱신 책임을 플랫폼으로 옮겼지만, 배송의 임시 JDBC 경로가 후속 철거 대상으로 남았다.

두 모듈의 `DeliveryStatusChangedListener`가 같은 기본 빈 이름을 사용해 컴포넌트 스캔에서 충돌한다. 이름만 분리하면 같은 이벤트를 받아 `orders.delivery_status`를 JDBC와 JPA로 각각 변경하는 경로가 남는다. JDBC 변경은 JPA 영속성 컨텍스트에 반영되지 않으므로 실행 순서와 flush 시점에도 의존한다.

## 결정

**주문 배송 상태의 작성자는 플랫폼 JPA 경로 하나로 제한한다.**

```text
DeliveryLifecycleService (@Transactional)
→ Delivery 상태 변경·저장
→ common.DeliveryStatusChanged 발행
→ 플랫폼 주문 DeliveryStatusChangedListener (@EventListener)
→ SyncOrderDeliveryStatusService (@Transactional)
→ OrderRepository.findByIdForUpdate (PESSIMISTIC_WRITE)
→ Order.syncDeliveryStatus
→ 같은 트랜잭션에서 JPA 변경 감지로 커밋
```

- `DeliveryStatusChanged`는 `setty.common`의 기존 record를 유지한다. 필드는 `deliveryId`, `orderId`, `status`, `changedAt`이다.
- 배송 모듈은 이벤트를 발행만 한다. Order 상태 리스너·Repository·`OrderDeliveryState`를 제거한다.
- 플랫폼은 Order 엔티티를 조회해 순방향 전이와 멱등성을 검증한다.
- 기존 JDBC의 주문 행 잠금은 플랫폼의 JPA `PESSIMISTIC_WRITE` 조회로 유지한다. 일반 주문 조회에는 잠금을 추가하지 않는다.
- 기존 배송 리스너의 양수 식별자·필수 시각 검증을 플랫폼 동기화 서비스에서 유지한다.
- 매물 리스너는 `Listing` 상태 변경을 계속 담당한다. 두 플랫폼 리스너는 동기로 실행되며, 어느 쪽이 실패해도 발행자의 트랜잭션을 롤백한다.
- 배송 목록·상세의 JDBC Projection은 유지한다.

## 근거

- Order 테이블과 상태 규칙을 플랫폼이 함께 소유한다.
- 중복 빈과 중복 갱신 경로를 함께 제거한다.
- 관리 중인 Order와 DB의 상태 변경을 JPA 영속성 컨텍스트에서 조정한다.
- 이벤트 계약, HTTP API와 DB 스키마를 변경하지 않는다.

## 결과

### 긍정

- 주문 상태 갱신 경로와 상태 검증 모델이 하나로 줄어든다.
- 배송 모듈에서 Order 테이블 구조에 직접 의존하지 않는다.
- 같은 상태 이벤트는 추가 Order 변경 없이 종료한다.
- 기존 Delivery·Order·Listing 트랜잭션 경계를 유지한다.

### 부정·제약

- 비관적 잠금은 트랜잭션 종료까지 유지되므로 같은 주문에 대한 변경은 대기할 수 있다.
- Order 행 잠금은 Delivery 자체의 동시 수락을 보장하지 않는다. 배송 Aggregate의 동시성 정책 변경은 이번 결정의 비범위다.
- 단일 서버와 DB를 전제로 한다. 서비스 분리 시 별도 일관성 전략이 필요하다.

## 거부한 대안

- **빈 이름 또는 리스너 순서만 변경: 거부.** 두 Order 작성자와 JDBC·JPA 상태 관리의 중복이 남는다.
- **수동 flush 추가: 거부.** 실행 순서 일부만 조정하고 중복 책임은 제거하지 못한다.
- **배송 JDBC 경로만 유지: 거부.** Order 갱신을 플랫폼이 담당한다는 소유권 결정에 맞지 않는다.
- **비동기·AFTER_COMMIT 전환: 거부.** Order 갱신 실패 시 이미 커밋된 Delivery를 함께 롤백할 수 없다.

## 검증

- 컴포넌트 스캔에서 빈 이름 충돌이 없는지 확인한다.
- 배송 수락·수령·완료 후 Delivery·Order 상태와 매물 상태를 검증한다.
- 상태 변경마다 Order의 JPA UPDATE가 한 번 발생하고 중복 이벤트에는 증가하지 않는지 검증한다.
- 같은 트랜잭션에 먼저 로드한 Order가 연속 상태 변경을 반영하는지 검증한다.
- Order 동기화 실패 시 Delivery·Listing 변경이 롤백되는지 검증한다.
- 주문 행 잠금이 외부 트랜잭션 종료까지 유지되는지 실제 MySQL에서 검증한다.
- 배송 코드에 `UPDATE orders`와 Order 상태 쓰기 Repository가 없는지 검색한다.

[문서 인덱스](../README.md) · [배송 도메인 설계](../domain-design.md) · [배송 모듈 구조](../module-architecture.md)
