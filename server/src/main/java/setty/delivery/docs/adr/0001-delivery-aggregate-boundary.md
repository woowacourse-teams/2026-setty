# ADR-0001: Delivery Aggregate 경계와 ID 참조

## 상태

**채택**

## 맥락

SETTY는 하나의 애플리케이션과 하나의 MySQL DB를 사용하는 모놀리식이다.

플랫폼은 주문 생성 후 `OrderRequested` 이벤트를 발행하고, 배송 모듈은 이를 받아 `Delivery`를 생성한다. 주문과 배송 데이터는 같은 트랜잭션에서 변경될 수 있지만 두 객체의 생명주기와 변경 책임은 서로 다르다. Order는 플랫폼, Delivery는 배송, 기사 계정은 배송 인증 하위 도메인이 관리한다.

Delivery의 상태 전이에는 주문 전체 정보나 기사 계정 전체 정보가 아니라 주문 식별자와 담당 기사 식별자만 필요하다.

같은 DB를 사용한다는 이유로 `Delivery`에서 `Order`와 `Driver`를 JPA 연관관계로 참조하면 모듈 간 객체 경계가 사라진다. 지연 로딩, cascade, 영속성 컨텍스트 전파와 양방향 탐색도 배송 규칙에 불필요한 결합을 추가한다.

반대로 모든 값을 원시 `Long`과 `String`으로만 표현하면 주문 ID, 배송 ID, 기사 ID가 컴파일 시점에 구분되지 않는다. 배송 경로와 배정 정보가 여러 개의 독립 필드로 흩어져 필수 조합도 표현하기 어렵다.

따라서 Delivery Aggregate의 경계, 외부 Aggregate 참조 방식과 내부 Value Object의 포함 관계를 명시할 필요가 있다.

## 결정

**`Delivery`를 배송 핵심 도메인의 Aggregate Root로 둔다.**

```text
Delivery
├── OrderId
├── FurnitureInfo
├── DeliveryRoute
│   ├── pickup Address
│   ├── destination Address
│   ├── pickup PhoneNumber
│   └── destination PhoneNumber
├── EstimatedDeliveryFee
└── DeliveryAssignment (수락 전 없음)
    ├── DriverId
    └── acceptedAt
```

세부 결정은 다음과 같다.

1. `Delivery`만 배송 상태 전이를 수행한다.
2. Order와 담당 기사는 `OrderId`, `DeliveryAssignment`의 `DriverId`로만 참조하고 JPA Entity 연관관계를 만들지 않는다.
3. `FurnitureInfo`, `DeliveryRoute`, `EstimatedDeliveryFee`, `DeliveryAssignment`를 포함 VO로 둔다.
4. `DeliveryAssignment`는 수락 전에는 없고, 수락 시 기사와 `acceptedAt`이 함께 생성된다.
5. `DeliveryId`는 JPA 내부 `Long id`를 Application과 API 경계에 표현하는 VO다.
6. `DeliveryRequest`와 `Shipment`는 Entity가 아닌 조회 Projection으로 둔다.
7. 기사 계정 `DeliveryMember`는 인증 하위 도메인의 별도 Aggregate Root로 유지한다.

JPA Entity와 Domain 객체는 현재 분리하지 않는다. `Delivery`와 합성 VO에 `@Entity`, `@Embeddable`을 직접 적용한다. 모놀리식 현재 규모에서 별도 영속성 모델과 매퍼를 추가하는 비용을 피하기 위한 의도된 제약이다.

## 근거

1. **변경 책임 분리** — 객체 연관관계를 제거해 상대 Aggregate의 내부 변경과 생명주기에 직접 영향받지 않는다.
2. **상태 불변식 집중** — 상태 전이, 담당 기사 확인과 상태별 시각 기록을 `Delivery`가 함께 수행한다.
3. **식별자 타입 안전성** — 같은 `Long` 값이어도 Order, Delivery, Driver 식별자를 메서드 시그니처에서 구분한다.
4. **필수 조합 표현** — 경로의 네 값과 배정의 두 값을 합성 VO로 묶어 부분 상태를 막는다.
5. **조회 모델 분리** — 필요한 컬럼만 JDBC Projection으로 읽고 조회 요구로 Aggregate를 확장하지 않는다.
6. **영속성 부작용 제한** — cascade 없이 ID 값만 저장하고 외부 Aggregate 저장으로 전파하지 않는다.

## 결과

### 긍정

- Delivery 상태 규칙의 변경 지점이 Aggregate Root로 단일화된다.
- Order와 Driver 패키지에 대한 컴파일·JPA 연관 의존성이 없다.
- VO 생성자에서 값과 필수 조합을 일찍 검증할 수 있다.
- 조회 API가 Aggregate 전체 로딩 없이 독립적으로 최적화된다.
- 배송과 Order 상태의 동기화는 객체 연관관계가 아니라 명시적인 Application 흐름으로 드러난다.

### 부정·제약

- `Delivery`만 조회해서 Order나 기사 상세 정보까지 탐색할 수 없다.
- JPA Entity와 Domain 객체를 공유하므로 Domain이 JPA 애노테이션에 의존한다.
- `Delivery.id`는 내부적으로 `Long`, 외부에서는 `DeliveryId`여서 Repository 어댑터 변환이 필요하다.
- 같은 `orderId`의 중복 생성을 Application 확인과 DB UNIQUE 제약이 함께 방어해야 한다.
- `OrderId`와 `DriverId` 값이 실제 외부 행을 가리키는지는 Delivery Aggregate 단독으로 검증할 수 없다.

## 거부한 대안

- **Order·Driver `@ManyToOne`: 거부.** 탐색 편의보다 Aggregate와 팀 소유권 결합 비용이 크다.
- **원시 `Long` 식별자: 거부.** 서로 다른 식별자의 의미가 사라지고 잘못된 값 전달을 막기 어렵다.
- **`DeliveryRequest`, `Shipment` Entity: 거부.** 저장 생명주기 없이 Delivery를 바라보는 조회 관점이다.
- **Domain과 JPA 모델 완전 분리: 보류.** 순수성은 높아지지만 영속성 모델과 양방향 매퍼 비용이 현재 이점보다 크다.

## 검증

- `Delivery`에 `@ManyToOne`, `@OneToOne`이 없는지 검색한다.
- `Delivery` 필드가 Order와 Driver Entity 대신 `OrderId`, `DeliveryAssignment`를 사용하는지 확인한다.
- `Delivery.request`, `accept`, `pickUp`, `complete` 도메인 테스트를 실행한다.
- VO의 ID 양수, 문자열 null·blank, 배송비 음수, 합성 필수 값 검증 테스트를 실행한다.
- 같은 `orderId` 이벤트 처리와 `delivery.order_id` UNIQUE 제약 통합 테스트를 실행한다.
- 목록 조회 SQL이 Delivery Aggregate 전체를 객체화하지 않는지 확인한다.

[문서 인덱스](../README.md) · [배송 도메인 설계](../domain-design.md) · [배송 모듈 구조](../module-architecture.md)
