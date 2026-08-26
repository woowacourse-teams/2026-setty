# delivery/ — 배송 도메인·기사 인증 (배송 팀 관리)

이 파일은 `server/src/main/java/setty/delivery/` 내부를 다룰 때 읽는다.
공통 규칙은 [`server/AGENTS.md`](../../../../../AGENTS.md)가 원본이다.

## 소유권

- 배송 팀은 `delivery/**`만 수정한다.
- `platform/**`은 읽기만 한다. Order Entity와 플랫폼 Repository를 배송 코드에서 직접 사용하지 않는다.
- `common/**`은 팀 간 계약이다. 양 팀 합의 없이 `DeliveryStatus`, `OrderRequested`를 수정하지 않는다.
- `global/**`은 플랫폼 팀 관리 영역이다. 배송 오류 코드를 허용된 구역에 추가하는 경우 외에는 직접 수정하지 않는다.
- `orders.delivery_status`, `orders.driver_id` 값의 UPDATE는 배송 팀 책임이다. 나머지 Order 컬럼은 수정하지 않는다.

## 작업별 참조 문서

현재 작업과 일치하는 행의 문서만 읽는다. 표에 있다는 이유만으로 모든 문서를 읽지 않는다.

| 작업 | 해당 작업에서 읽을 문서 |
|---|---|
| 문서 위치와 변경 라우팅 | [배송 문서 인덱스](docs/README.md) |
| Aggregate, VO, 상태 전이 | [배송 도메인 설계](docs/domain-design.md) |
| Service, 이벤트, Repository, API | [배송 모듈 구조](docs/module-architecture.md) |
| Aggregate 경계 변경 | [ADR-0001](docs/adr/0001-delivery-aggregate-boundary.md) |
| Order 상태 동기화 변경 | [ADR-0002](docs/adr/0002-synchronous-order-status-sync.md) |
| 예외·오류 응답 변경 | [서버 예외 처리 규칙](../../../../../docs/exception-handling.md) |

새 결정이 기존 ADR과 충돌하면 기존 ADR을 조용히 수정하지 않는다. 새 ADR을 발행하고 대체 관계를 기록한다.

## 컨텍스트 로딩 제한

- 매 작업마다 `docs/adr/` 전체를 읽지 않는다.
- 먼저 작업 범위를 확정하고 위 표에서 직접 연결된 문서만 읽는다.
- 관련 ADR을 모르면 파일명과 제목만 검색한 뒤 후보 ADR만 전문을 읽는다.
- 선택한 ADR이 `대체됨`이거나 다른 ADR을 명시적으로 참조할 때만 연결된 ADR을 추가로 읽는다.
- 단순 코드·서식·문서 변경에 관련 ADR이 없으면 ADR을 읽지 않는다.

## 패키지 구조

- `api`: Controller, HTTP DTO, 배송 API 예외 처리
- `application`: Service, Listener, Repository 인터페이스
- `application.event`: 배송 모듈 소유 이벤트
- `application.query`: JDBC 조회 Projection
- `domain`: Delivery Aggregate, VO, 상태 모델
- `persistence`: JPA, Spring Data, JDBC 구현체
- `auth`: 기사 인증 하위 도메인의 api/application/domain/persistence

`controller`, `repository`, `infrastructure`, `application.port` 패키지를 추가하지 않는다.

## Domain 규칙

- `Delivery`만 배송 핵심 Aggregate Root다.
- `DeliveryMember`는 인증 하위 도메인의 별도 Aggregate Root다.
- Order와 기사 계정은 `OrderId`, `DriverId`로만 참조한다.
- `@ManyToOne`, `@OneToOne` 등 외부 Entity 연관관계를 만들지 않는다.
- 상태는 `REQUESTED → ACCEPTED → PICKED_UP → DELIVERED` 순서로만 변경한다.
- 상태 변경은 `Delivery.request`, `accept`, `pickUp`, `complete`로만 수행한다.
- Domain에서 현재 시각, SecurityContext, HTTP DTO를 조회하거나 참조하지 않는다.
- setter, `@Data`, 범용 `@Builder`를 Entity와 VO에 추가하지 않는다.
- 검증 생성자와 JPA용 `protected` 기본 생성자는 Lombok 생성자로 대체하지 않는다.

Domain 규칙을 바꾸면 코드와 [배송 도메인 설계](docs/domain-design.md)를 함께 수정한다.

## Application·이벤트 규칙

- Service가 트랜잭션과 Repository 호출을 조정하고 Domain 규칙을 다시 구현하지 않는다.
- `DriverId`, `Instant`는 API 또는 이벤트 경계에서 생성해 Service 입력으로 전달한다.
- `OrderRequested`는 `common` 계약을 재사용하고 배송 패키지에 중복 정의하지 않는다.
- `DeliveryStatusChanged`는 `delivery.application.event`가 소유한다.
- Listener 클래스명은 `...Listener`, 단일 처리 메서드명은 `handle`을 사용한다.
- 상태 이벤트는 동기 `@EventListener`로 처리한다.
- `@TransactionalEventListener(AFTER_COMMIT)`, `@Async`, 내부 HTTP 통신을 사용하지 않는다.
- Listener 예외가 발행자의 트랜잭션을 롤백하도록 유지한다.

## Repository·조회 규칙

- Repository 인터페이스는 `application`, 구현체는 `persistence`에 둔다.
- Application에 `JpaRepository`를 노출하지 않는다.
- Spring Data 인터페이스는 package-private으로 유지한다.
- Delivery 상태별 `updateStatus` Repository 메서드를 만들지 않는다.
- Aggregate를 조회하고 Domain 메서드를 호출한 뒤 저장한다.
- 목록과 상세 조회는 JDBC Projection을 사용하고 Aggregate 전체를 로딩하지 않는다.
- `DeliveryRequest`, `Shipment`를 Entity나 Aggregate로 만들지 않는다.
- 목록에는 전화번호를 조회·노출하지 않고 상세에서만 제공한다.

## API·인증 규칙

- Controller는 인증 기사 추출, 입력·응답 변환과 Service 위임만 담당한다.
- `driverId`를 URI, Query Parameter, Request Body로 받지 않는다.
- 상태 변경 POST 성공 응답은 `204 No Content`와 빈 Body다.
- 다른 기사의 배송 접근은 외부에 `404`로 표현한다.
- 비밀번호 원문을 저장·로그·응답에 노출하지 않는다.
- 로그인 실패 응답으로 계정 존재 여부를 구분하지 않는다.

## 코드 작성 규칙

- `final` 의존성을 주입받는 Spring Bean은 `@RequiredArgsConstructor`를 사용한다.
- Domain 검증 생성자는 명시적으로 작성한다.
- 이벤트와 조회·응답 DTO는 Java record를 우선한다.
- 예외 클래스는 `BusinessException` 하나만 사용한다.
- ErrorCode와 HTTP 응답을 변경하기 전에 서버 예외 처리 원본을 읽는다.
- 스키마 변경은 `schema.sql`에 반영하고 `ddl-auto: validate`를 유지한다.

## 검증

- Domain 상태·VO 변경은 Spring 없는 단위 테스트로 검증한다.
- 이벤트·트랜잭션 변경은 상태 일치, 멱등성, 롤백을 검증한다.
- MySQL DDL, UNIQUE, `FOR UPDATE`, 동시성은 실제 MySQL로 검증한다.
- H2 MySQL Mode 결과를 실제 MySQL 검증으로 간주하지 않는다.
- 단순 리팩터링은 우선 `./gradlew compileJava --no-daemon`으로 검증한다.
- Docker·Testcontainers 전체 테스트는 사용자 승인 없이 실행하지 않는다.
- 테스트에는 가상 주소와 전화번호만 사용한다.
