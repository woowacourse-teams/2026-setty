# 배송 모듈 구조

배송 도메인을 실행하는 API, Application, 이벤트, Repository와 Persistence 구조를 정의한다. 도메인 객체와 불변식은 [배송 도메인 설계](domain-design.md)가 원본이다.

[문서 인덱스](README.md) · [ADR-0001](adr/0001-delivery-aggregate-boundary.md) · [ADR-0004](adr/0004-platform-jpa-order-status-sync.md)

## 1. 패키지와 의존 방향

```text
delivery/
  api/                    HTTP Controller, 응답 DTO, 배송 예외 처리
  application/            Service, Listener, Repository 계약
    query/                JDBC 조회 Projection
  domain/                 Delivery Aggregate, VO, 상태 모델
  persistence/            JPA, Spring Data, JDBC 구현체
  auth/
    api/                  기사 인증 HTTP API
    application/          인증 유스케이스와 Repository 계약
    domain/               DeliveryMember
    persistence/          기사 계정 JPA 구현체
  docs/                   설계 문서와 ADR
```

의존 방향은 다음과 같다.

```text
api → application → domain
             ↑
       persistence
```

- API는 Application Service에 위임한다.
- Application은 유스케이스와 트랜잭션을 조정한다.
- Persistence는 Application의 Repository 계약을 구현한다.
- Domain은 Spring Web, SecurityContext, API DTO를 알지 않는다.
- `application.port`, `infrastructure`, `controller`, `repository` 패키지는 사용하지 않는다.

## 2. Command 유스케이스

| 유스케이스 | 진입 | Domain 호출 | 결과 |
|---|---|---|---|
| 배송 등록 | `OrderRequestedListener` | `Delivery.request(...)` | `REQUESTED` Delivery 저장 |
| 배송 수락 | `DeliveryLifecycleService.accept(...)` | `Delivery.accept(...)` | Delivery와 Order를 `ACCEPTED`로 변경 |
| 가구 수령 | `DeliveryLifecycleService.pickUp(...)` | `Delivery.pickUp(...)` | 두 상태를 `PICKED_UP`으로 변경 |
| 배송 완료 | `DeliveryLifecycleService.complete(...)` | `Delivery.complete(...)` | 두 상태를 `DELIVERED`로 변경 |

- `DeliveryLifecycleService`는 상태별 메서드에서 `DeliveryId`, `DriverId`, `Instant`를 입력으로 받는다.
- 현재 기사와 현재 시각은 API 또는 이벤트 경계에서 구한다.
- Application과 Domain에서 SecurityContext를 직접 조회하지 않는다.
- Repository가 상태를 직접 변경하지 않고 Aggregate 메서드를 호출한 결과를 저장한다.

### 배송 등록 멱등성

```text
OrderRequested 수신
→ existsByOrderId 확인
→ Delivery.request
→ Delivery 저장
```

- 같은 `orderId`가 이미 존재하면 성공으로 종료한다.
- `delivery.order_id` UNIQUE 제약을 최종 중복 방어선으로 사용한다.

## 3. 이벤트와 트랜잭션

### 이벤트 소유권

| 이벤트 | 위치 | 역할 |
|---|---|---|
| `OrderRequested` | `setty.common` | 플랫폼이 발행하고 배송이 수신하는 팀 간 계약 |
| `DeliveryStatusChanged` | `setty.common` | 배송이 발행하고 플랫폼이 Order·Listing 상태를 동기화하는 팀 간 계약 |
| `DeliveryRequestsChanged` | `setty.delivery.application` | 배송 등록·수락 후 기사 앱 요청 목록 재조회를 알리는 내부 이벤트 |

공용 이벤트를 배송 패키지에 중복 정의하지 않는다. 배송은 `DeliveryStatusChanged`를 발행만 하며, 플랫폼의 주문·매물 리스너가 각 소유 엔티티를 갱신한다.

### 상태 변경 흐름

```text
Delivery 조회
→ Domain 상태 전이
→ Delivery 저장
→ DeliveryStatusChanged 발행
→ 플랫폼 주문 DeliveryStatusChangedListener 동기 실행
→ SyncOrderDeliveryStatusService
→ OrderRepository.findByIdForUpdate (JPA PESSIMISTIC_WRITE)
→ Order.syncDeliveryStatus 검증·변경
→ JPA 변경 감지로 Order 배송 상태 저장
→ 하나의 트랜잭션으로 커밋
```

- 상태 변경 Service와 Listener는 같은 트랜잭션에 참여한다.
- Listener 예외는 Delivery와 Order 변경을 모두 롤백한다.
- 플랫폼의 매물 리스너도 같은 이벤트를 동기 처리한다. 주문·매물 리스너의 실행 순서에 의존하지 않는다.
- 상태 동기화에는 `@TransactionalEventListener(AFTER_COMMIT)`, `@Async`, 내부 HTTP 통신을 사용하지 않는다.
- 동일 상태 이벤트는 멱등 처리하고 잘못된 이전 상태는 거부한다.

선택 근거와 대안은 [ADR-0004](adr/0004-platform-jpa-order-status-sync.md)에 기록한다.

### 기사 앱 요청 목록 알림

```text
Delivery 저장 또는 수락 커밋
→ DeliveryRequestsChanged
→ DeliveryRequestsChangedListener (AFTER_COMMIT)
→ SSE delivery-requests-changed
→ 기사 앱 GET /requests 재조회
```

- 이벤트는 목록 데이터가 아닌 재조회 신호만 전송한다.
- SSE 전송 실패는 이미 커밋된 배송을 되돌리지 않고 경고 로그만 남긴다.
- 이 `AFTER_COMMIT` 사용은 외부 UI 알림에만 한정한다. 상태·주문 데이터 변경에는 사용하지 않는다.

선택 근거와 제약은 [ADR-0005](adr/0005-delivery-request-sse.md)에 기록한다.

## 4. Repository 경계

### Command

| 계약 | 구현 | 책임 |
|---|---|---|
| `DeliveryRepository` | `JpaDeliveryRepository` | 중복 확인, Delivery 단건 조회·저장 |
| `DeliveryMemberRepository` | `JpaDeliveryMemberRepository` | 기사 계정 조회·저장 |

- Repository 인터페이스는 `application`, 구현체는 `persistence`에 둔다.
- Spring Data 인터페이스는 package-private으로 유지한다.
- `JpaDeliveryRepository`가 `DeliveryId`와 JPA `Long` 식별자 사이를 변환한다.
- Application에 `JpaRepository` 전체 API를 노출하지 않는다.
- Delivery에 상태별 `updateStatus` Repository 메서드를 만들지 않는다.
- Order 상태의 조회·잠금·저장은 플랫폼의 JPA `OrderRepository`가 담당한다. 배송 모듈에 Order 쓰기 Repository를 두지 않는다.

### Query

`DeliveryQueryRepository`는 `JdbcDeliveryQueryRepository`가 구현한다.

- 목록과 상세에 필요한 컬럼만 조회한다.
- `DeliveryRequest`와 `Shipment` Projection에 직접 매핑한다.
- Delivery Aggregate 전체를 객체화하지 않는다.
- 요청 목록은 `REQUESTED`이면서 미배정인 데이터만 조회한다.
- 내 배송은 현재 기사에게 배정된 상태만 조회한다.
- 목록에는 전화번호를 조회하지 않고 상세에서만 조회한다.

## 5. API와 인증 경계

- Controller는 인증 기사 추출, 입력 변환, DTO 변환과 Service 위임만 담당한다.
- `driverId`를 URI, Query Parameter, Request Body로 받지 않는다.
- 상태 변경 POST 성공 응답은 `204 No Content`와 빈 Body다.
- 다른 기사의 배송은 존재 여부를 숨기기 위해 `404`로 표현한다.
- HTTP DTO는 Domain 패키지에 두거나 Entity로 사용하지 않는다.
- 기사 인증은 `delivery.auth` 하위 도메인이 담당한다.
- `Delivery`는 `DeliveryMember` 대신 인증된 회원 ID를 `DriverId`로 받는다.

예외 클래스와 응답 규칙은 [`server/docs/exception-handling.md`](../../../../../../docs/exception-handling.md)가 원본이다.

## 6. 영속성 제약

- `delivery.order_id`는 NOT NULL·UNIQUE다.
- 수락 전 `driver_id`, `accepted_at`은 null이다.
- `picked_up_at`, `delivered_at`은 해당 상태에 도달하기 전 null이다.
- 필수 VO 값과 `requested_at`, `status`는 null을 허용하지 않는다.
- Order와 Driver로 향하는 JPA Entity 연관관계를 만들지 않는다.
- 스키마는 `schema.sql`을 원본으로 하고 Hibernate `ddl-auto`는 `validate`를 유지한다.
- MySQL DDL, UNIQUE, `FOR UPDATE`, 롤백과 동시성은 실제 MySQL로 검증한다.
