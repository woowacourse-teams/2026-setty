# ADR-0003: Order 배송 상태 JDBC 접근을 임시 사용

## 상태

**임시 채택**

## 맥락

Delivery와 Order의 배송 상태는 같은 트랜잭션에서 변경되어야 한다.

현재 배송 모듈은 `JdbcOrderDeliveryStatusRepository`를 통해 `orders.delivery_status`를 직접 조회하고 변경한다. 이 방식은 플랫폼 팀과 Order 상태 변경 경계를 합의하기 전에 작업을 진행하기 위해 도입했다.

따라서 현재 JDBC 구현을 최종 모듈 경계로 확정하지 않는다.

## 임시 결정

플랫폼 팀과 합의하기 전까지 현재 JDBC 구현을 유지한다.

- `id`, `delivery_status`만 조회한다.
- 상태 검증을 위해 대상 행을 `FOR UPDATE`로 조회한다.
- `delivery_status`만 변경한다.
- 다른 Order 컬럼으로 접근 범위를 확장하지 않는다.
- 플랫폼 패키지 코드를 수정하지 않는다.

이 ADR은 [ADR-0002](0002-synchronous-order-status-sync.md)의 동기 트랜잭션 결정을 변경하지 않는다. Order 상태에 접근하는 Persistence 구현이 임시라는 사실만 기록한다.

## 결과

### 긍정

- 플랫폼 팀 합의 전에도 상태 동기화와 롤백을 검증할 수 있다.
- 임시 구현의 이유와 허용 범위가 코드 밖에 남는다.

### 부정·제약

- 배송 모듈이 Order 테이블 구조에 직접 의존한다.
- 플랫폼의 공식 변경 경계가 결정되면 구현 교체가 필요할 수 있다.
- 현재 구현이 별도 합의 없이 영구 구조로 굳어질 위험이 있다.

## 종료 조건

플랫폼 팀과 Order 배송 상태 변경 책임과 호출 경계를 합의하면 이 결정을 재검토한다.

- 합의된 구현으로 교체하거나 현재 JDBC 접근을 공식화한다.
- 결론은 새 ADR로 기록한다.
- 새 ADR이 채택되면 이 ADR의 상태를 `대체됨`으로 변경한다.

## 검증

- `JdbcOrderDeliveryStatusRepository`가 `delivery_status` 외 Order 컬럼을 변경하지 않는지 확인한다.
- 배송 작업에서 플랫폼 패키지 코드 변경이 없는지 확인한다.
