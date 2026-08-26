# ADR-0002: 동기 이벤트로 Delivery와 Order 상태를 함께 변경

## 상태

**채택**

## 맥락

Delivery 상태가 `ACCEPTED`, `PICKED_UP`, `DELIVERED`로 변경되면 Order의 배송 상태도 동일하게 변경되어야 한다.

SETTY는 하나의 애플리케이션과 하나의 DB를 사용하는 모놀리식이다. Delivery와 Order가 서로 다른 패키지와 책임을 가지지만 동일한 트랜잭션 자원을 사용할 수 있다.

다음 실패 조건을 만족해야 한다.

- Delivery 변경이 실패하면 Order는 변경되지 않는다.
- Order 변경이 실패하면 Delivery 변경도 롤백된다.
- 외부 트랜잭션에서는 두 상태의 중간 불일치가 관찰되지 않는다.
- Delivery가 플랫폼의 Order Entity나 `OrderRepository`에 직접 의존하지 않는다.
- 동일한 상태 이벤트가 다시 처리되어도 결과가 달라지지 않는다.

비동기 이벤트나 커밋 후 Listener를 사용하면 일시적인 상태 불일치와 별도 보상 처리가 필요하다. 현재 모놀리식 범위에는 이를 정당화할 분산 시스템 요구가 없다.

## 결정

**Delivery 상태 변경 후 동기 `DeliveryStatusChanged` 이벤트를 발행하고 같은 트랜잭션에서 Order 배송 상태를 변경한다.**

```text
Delivery 조회
→ Delivery 도메인 메서드 호출
→ Delivery 저장
→ DeliveryStatusChanged 발행
→ DeliveryStatusChangedListener 동기 실행
→ orders 행 SELECT FOR UPDATE
→ OrderDeliveryState.synchronizeTo
→ orders.delivery_status 저장
→ 함께 커밋
```

세부 결정은 다음과 같다.

1. `AcceptDeliveryService`, `PickupDeliveryService`, `CompleteDeliveryService`를 트랜잭션 경계로 둔다.
2. 각 Service는 Aggregate 상태 전이 후 `DeliveryStatusChanged`를 발행한다.
3. 이벤트는 배송 모듈의 `application.event`가 소유한다.
4. Listener는 `@EventListener`로 동기 실행한다.
5. Listener의 트랜잭션은 발행자의 기존 트랜잭션에 참여한다.
6. Order는 전체 Entity가 아닌 `OrderDeliveryState`로 상태 규칙만 표현한다.
7. `JdbcOrderDeliveryStatusRepository`가 대상 행을 `FOR UPDATE`로 잠근다.
8. Listener 예외는 발행자까지 전파해 전체 변경을 롤백한다.

### 상태 동기화 규칙

| 이벤트 상태 | 기대 이전 상태 | 결과 |
|---|---|---|
| `ACCEPTED` | `REQUESTED` | `ACCEPTED`로 변경 |
| `PICKED_UP` | `ACCEPTED` | `PICKED_UP`으로 변경 |
| `DELIVERED` | `PICKED_UP` | `DELIVERED`로 변경 |

- 현재 Order 상태가 이벤트 상태와 같으면 멱등 성공으로 처리한다.
- 기대 이전 상태와 이벤트 상태 모두 아니면 `ORDER_DELIVERY_STATUS_MISMATCH`를 발생시킨다.
- `REQUESTED` 상태 이벤트는 허용하지 않는다.

## 근거

1. **원자성** — 같은 DB 트랜잭션을 사용해 두 상태를 함께 커밋하거나 함께 롤백한다.
2. **경계 유지** — Delivery Aggregate와 상태 변경 Service가 플랫폼 Repository를 직접 참조하지 않는다.
3. **명시적 규칙** — Order 상태의 이전 상태와 멱등성 판단을 `OrderDeliveryState`가 표현한다.
4. **동시성 제어** — Order 행 잠금으로 동시에 들어오는 상태 변경의 순서를 직렬화한다.
5. **현재 규모 적합성** — Outbox, Inbox, 보상 트랜잭션 없이 모놀리식 자원을 그대로 활용한다.

## 결과

### 긍정

- Delivery와 Order 상태 불일치가 커밋되지 않는다.
- Listener 실패가 호출자에게 즉시 전달된다.
- 중복 이벤트가 추가 상태 변경을 만들지 않는다.
- 내부 HTTP와 분산 트랜잭션이 필요 없다.

### 부정·제약

- 이벤트 Listener가 같은 트랜잭션 안에서 Order 행 잠금과 UPDATE를 수행해 트랜잭션 시간이 늘어난다.
- 이벤트 발행은 구조적 분리를 제공하지만 실행은 비동기가 아니다.
- 향후 서비스가 분리되면 현재 원자적 트랜잭션을 그대로 유지할 수 없다.
- Order 스키마의 배송 상태 컬럼 계약에 배송 모듈이 의존한다.

## 거부한 대안

- **`AFTER_COMMIT` Listener: 거부.** Order 변경 실패 시 이미 커밋된 Delivery를 롤백할 수 없다.
- **비동기 이벤트: 거부.** 일시적 불일치, 재시도, Inbox·Outbox가 추가된다.
- **내부 HTTP 호출: 거부.** 단일 애플리케이션 안에 네트워크 실패 지점을 만든다.
- **Delivery Service에서 플랫폼 `OrderRepository` 직접 호출: 거부.** 패키지 소유권과 Aggregate 경계를 침범한다.

## 검증

- 수락·수령·완료 후 두 상태가 각각 동일한지 확인한다.
- Order 변경 예외가 발생하면 Delivery 변경도 롤백되는지 확인한다.
- Delivery 전이 예외 시 Order UPDATE가 실행되지 않는지 확인한다.
- 동일 이벤트를 두 번 발행해 두 번째 처리가 저장을 만들지 않는지 확인한다.
- 잘못된 Order 이전 상태에서 불일치 예외가 발생하는지 확인한다.
- 코드에 `AFTER_COMMIT`, `@Async`, 내부 HTTP 호출이 없는지 검색한다.

[문서 인덱스](../README.md) · [배송 도메인 설계](../domain-design.md) · [배송 모듈 구조](../module-architecture.md)
