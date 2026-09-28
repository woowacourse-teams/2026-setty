# dev JVM Heap·DB 커넥션 풀 지표

HTTP 지연·실패가 발생할 때 JVM 메모리 사용과 DB 연결 부족을 함께 확인한다. `DevMetricsConfig`가 기존 HTTP 지표 외에 아래 5개 지표 이름만 OTLP로 허용한다. Spring Boot가 이미 제공하는 JVM·HikariCP 자동 수집을 사용하며 별도 라이브러리나 Agent 설정 변경은 필요하지 않다.

| 지표 | 의미 | 데이터 포인트 라벨 |
| --- | --- | --- |
| `jvm.memory.used` | Heap 영역별 사용량(bytes) | `area=heap`, `id`(메모리 영역 이름) |
| `jvm.memory.max` | Heap 영역별 최대량(bytes), 미정이면 -1 | `area=heap`, `id` |
| `hikaricp.connections.active` | 풀에서 빌려 사용 중인 연결 수 | `pool` |
| `hikaricp.connections.max` | 설정한 풀 최대 연결 수 | `pool` |
| `hikaricp.connections.pending` | 연결 획득을 기다리는 스레드 수 | `pool` |

Non-Heap, GC, 스레드 상세, JDBC 중복 지표, SQL·사용자별 라벨은 추가하지 않는다. HTTP의 `uri`·`method`·`outcome`과 서비스·환경 리소스 속성은 유지한다. 다른 프로필에서는 OTLP가 비활성화된다. 이 설정은 애플리케이션의 Hikari 풀 상태를 보여주며 DB 서버 전체의 연결 수·CPU·쿼리 실행 시간·락을 측정하지 않는다.

## 수집량과 해석 범위

- 1분 주기를 유지한다. 추가 시계열은 프로세스당 `Heap 영역 수 × 2 + Hikari 풀 수 × 3`이다. 예를 들어 Heap 3영역·풀 1개면 9개가 추가된다. 실제 영역 수는 JVM/GC에 따라 다르며 HTTP 경로 수와 곱해지지 않는다.
- 각 게이지는 전송 시점의 순간값이다. **1분 평균이나 최대값이 아니며**, 사이에 발생했다가 해소된 메모리·연결 대기 급증은 놓칠 수 있다. `pending=0`만으로 그 1분 동안 대기가 없었다고 단정하지 않는다. 짧은 병목은 HTTP p95·로그와 함께 판단하고, 필요하면 후속으로 연결 획득 시간이나 타임아웃 카운터를 검토한다.
- `active`는 SQL 실행 중인 연결만 의미하지 않는다. 트랜잭션 등에서 확보한 연결도 포함된다. 대기 스레드 수는 연결 수와 다르다.
- Heap 영역의 `id`를 제거하면 서로 다른 게이지가 충돌해 일부 값만 남을 수 있어 보존한다. `jvm.memory.used`는 영역별 값을 합쳐 전체 사용량을 볼 수 있다. `max=-1`은 최대량 미정이며 장애나 음수 메모리 사용을 뜻하지 않는다. GC마다 최대량 정의가 달라 **영역별 max를 무조건 합해 전체 Heap 한도나 사용률을 계산하지 않는다.**
- 게이지에는 요청 수처럼 `rate`/`increase`를 적용하지 않는다. 0은 정상일 수 있지만 빈 결과는 0으로 치환하지 않고 수집 상태부터 확인한다.
- 실제 수집 비용은 CloudWatch 사용량으로 확인한다. 이 변경으로 고정 비용이나 월 비용을 실측한 것은 아니다.

## 저장소 검증

```sh
./gradlew test --tests 'setty.global.metrics.*'
```

- 프로필 설정 테스트: dev만 OTLP 전송기를 만들고 기본/local/prod는 비활성화.
- 필터 테스트: Heap 영역·DB 풀 구분을 보존하고 허용하지 않은 지표 및 추가 라벨 제외.
- HTTP 통합 테스트: 실제 HTTP 요청의 경로별 누적 히스토그램이 Heap 게이지와 함께 전송됨.
- 자원 통합 테스트: 실제 JVM과 Hikari 풀을 Boot 자동 설정에 연결하고 로컬 OTLP HTTP 수신기의 protobuf를 확인. 풀 최대 1개를 빌린 상태에서 다른 스레드를 대기시켜 `active=1`, `pending=1`, `max=1`을 확인한 뒤, 반환 후 `active=0`, `pending=0`을 확인한다. JDBC 연결만 테스트 대역을 사용하며 외부 DB에 접속하지 않는다. 실제 MySQL 통신이나 dev CloudWatch 수신은 이 테스트의 검증 범위가 아니다.

## dev 배포 후 확인

1. dev 프로필·OTLP 전송 로그를 확인한다. [HTTP 지표 문서](dev-http-metrics.md#배포-후-확인)의 절차를 따른다. 기존 Agent·로그·EC2 CPU/메모리 설정을 다시 설치하거나 바꾸지 않는다.
2. DB를 사용하는 정상 API에 요청한 후 두 번 이상의 1분 전송 주기를 기다린다. Hikari는 풀이 초기화된 후 지표가 등록되므로 health만 호출해서 DB 연결을 검증했다고 보지 않는다.
3. CloudWatch Query Studio에서 최근 30분 범위로 아래 지표를 각각 조회한다. 처음에는 집계 없이 조회해 EC2 인스턴스, `id`/`pool`, 단위를 확인한다. 아래 쿼리의 AWS 실측은 배포 후 별도로 확인해야 한다.

```promql
{"jvm.memory.used","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev",area="heap"}
```

```promql
{"jvm.memory.max","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev",area="heap"}
```

```promql
{"hikaricp.connections.active","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}
```

```promql
{"hikaricp.connections.max","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}
```

```promql
{"hikaricp.connections.pending","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}
```

Heap 사용량은 bytes이고 MiB로 보려면 `/ 1024 / 1024`를 붙인다. 여러 인스턴스가 함께 조회되면 리소스의 `@resource.host.id`로 대상 인스턴스를 선택한 후 합산한다. `active`/`pending`은 유휴 시 0이어도 정상이며, 값 변화를 보기 위해 dev DB에 의도적으로 오래 걸리는 쿼리나 잠금을 만들 필요는 없다. 대기 변화는 위 로컬 테스트로 검증하고 실제 dev 부하 비교는 별도 단계에서 진행한다.

4. 기존 HTTP 경로별 count·5xx·p95, `/setty/dev/backend` 로그와 `Setty/Dev` 메모리의 최신 데이터도 확인한다. 신규 지표 수신과 기존 수집 유지가 확인되면 대시보드 구성, 작은 dev 부하 테스트, 기준값에 따른 알림 순으로 이어간다.

참고: [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [Micrometer JVM Metrics](https://docs.micrometer.io/micrometer/reference/reference/jvm.html). 실제 이름과 의미는 프로젝트가 사용하는 Micrometer 1.17.0·HikariCP 7.0.2 소스 및 OTLP 통합 테스트로 확인한다.
