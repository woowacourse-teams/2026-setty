# 배송 목록의 단일·복수 복합 인덱스 비교

[배송 목록 조회](../../src/main/java/setty/delivery/persistence/JdbcDeliveryQueryRepository.java)에 필요한 인덱스를 고르기 위해 네 가지 사용 경로를 비교한다. [V10](../../src/main/resources/db/migration/V10__add_delivery_query_index.sql)은 `idx_delivery_requests(status, driver_id, requested_at, id)` **하나만 추가**한다.

미배정 인덱스는 요청 범위와 최신순 조회를 지원하고, 기사별 조회도 `status IN (...)`과 `driver_id`를 조합한 범위로 선별한다. 기사별 결과의 정렬은 남지만 아래 50건 조건에서는 두 번째 인덱스의 추가 효과가 작았다. 기사별 전용 인덱스는 보류한다.

## 실행

Java 21과 Docker가 필요하다. 저장소 루트의 `server/`에서 실행한다.

```bash
./gradlew test -I performance/local-delivery-index/benchmark.gradle \
  --tests setty.performance.DeliveryIndexComparisonTest
python3 performance/local-delivery-index/analyze.py build/reports/delivery-index
```

실험 소스는 일반 `test`와 CI 소스 경로에 포함되지 않는다. MySQL 8.4.11 Testcontainers의 새 DB에 실제 Flyway 마이그레이션을 적용하며 dev·prod DB에는 접속하지 않는다. 결과는 Git 제외 경로 `build/reports/delivery-index/`에 저장한다. 출력 경로는 Gradle에 `-Ddelivery.index.output=경로`를 넘겨 바꿀 수 있다.

## 입력과 측정 방법

| 분포 | 전체 | 미배정 요청 | 미배정 취소 이력 | 배정 이력 | 실험 기사 결과 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `assigned-history` | 50,000 | 500 | 0 | 49,500 | 50 |
| `cancelled-history` | 50,000 | 500 | 24,500 | 25,000 | 50 |

배정 이력은 `ACCEPTED`·`PICKED_UP`·`DELIVERED`로 나눈다. 취소 분포는 [배차 전에만 취소하고 기록을 유지하는 도메인 규칙](../../src/main/java/setty/delivery/domain/delivery/Delivery.java)을 따른다. 취소 이력은 `driver_id IS NULL`로 남으므로 기사 인덱스만 사용하면 요청과 함께 읽게 된다. 위 분포는 용량 비교용 합성 조건이며 실제 상태 비율이나 트래픽 예측이 아니다.

V10 적용 뒤 기사별 후보 `idx_delivery_driver_shipments(driver_id, accepted_at, id)`를 **실험 컨테이너에만** 추가한다. `IGNORE INDEX`로 없음·기사 전용만·미배정 전용만·둘 다 허용하는 네 경로를 비교한다. 둘 다 허용하는 경로는 인덱스 힌트를 붙이지 않는다.

실제 `JdbcDeliveryQueryRepository`를 호출하고, 실험 전용 `JdbcTemplate`이 인덱스 제외 힌트만 삽입한다. 모든 경로의 반환 ID·컬럼 값·정렬 순서를 기준 결과와 대조한다. 각 분포·조회·경로별 워밍업 2회와 본 측정 6회를 실행하며 경로 순서를 회전한다. 워밍업 32회, 본 측정 96회다.

시간은 `EXPLAIN ANALYZE` 최상위 노드의 종료 시간(ms, `loops=1`)이다. JDBC 연결·전체 결과 수신·HTTP 응답 시간을 측정한 값이 아니다. MySQL 버퍼 풀은 128MiB이며 캐시를 강제로 초기화하지 않는다. 결과·입력의 일관성을 검사하지만 시간에 고정된 성공 기준을 두지 않는다.

## 관측 결과와 선택 — 2026-10-27

로컬 Docker MySQL 8.4.11, 경로별 본 측정 6회의 중앙값(ms)이다. 다른 환경에서 같은 시간이 보장되지는 않는다.

| 분포·조회 | 없음 | 기사 전용만 | 미배정 전용만 | 둘 다 |
| --- | ---: | ---: | ---: | ---: |
| 배정 이력·미배정 목록 | 11.850 | 0.744 | 0.650 | 0.639 |
| 배정 이력·기사별 내역 | 10.800 | 0.079 | 0.098 | 0.076 |
| 취소 이력·미배정 목록 | 11.250 | 15.600 | 0.557 | 0.650 |
| 취소 이력·기사별 내역 | 10.700 | 0.067 | 0.093 | 0.074 |

- 미배정 전용 인덱스는 두 분포 모두 요청 500행을 직접 조회하고 정렬을 생략했다.
- 기사 전용 인덱스로 미배정 목록을 읽으면 취소 분포에서 25,000행을 읽고 정렬했다. 미배정 인덱스가 이 누적 비용을 줄인다.
- 미배정 전용 인덱스만으로도 기사별 내역을 상태별 세 범위에서 총 50행으로 좁혔다. 50행의 정렬은 남지만, 기사 전용 인덱스의 추가 시간 감소는 약 0.02ms였다.

따라서 **미배정 전용 인덱스 하나를 채택**한다. 기사별 반환 건수가 크게 늘면 기사 전용 인덱스와 페이지 처리를 다시 검토한다. 추가 인덱스의 저장 공간과 쓰기 유지 비용은 별도로 측정하지 않았다. 이번 수치를 dev API나 실제 사용자 화면의 개선율로 해석하지 않는다.

`samples.json`은 모든 실행 계획·SQL·인자를, `metadata.json`은 DB 버전과 측정 조건을 보존한다. 분석 도구는 워밍업·본 측정 수, 반환 건수와 반복 번호를 확인하고 `samples.csv`·`comparison.json`을 만든다. 원본 결과는 보고서의 반올림 전 수치를 확인할 때 사용한다.
