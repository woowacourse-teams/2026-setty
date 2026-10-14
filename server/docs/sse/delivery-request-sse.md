# 배송 요청 SSE 연결 허용 구현 정책

**채택: 기사당 최대 2개, 인스턴스당 임시 연결 상한 1,000개를 적용하고 초과 신규 연결을 거부한다.** 기사당 2개는 재접속 중 이전 연결과 새 연결의 일시적 중첩을 허용하기 위한 정책이다. 1,000개는 현재 환경에서 추가 검증을 시작하기 위한 보호 상한이며, 검증된 운영 안전 용량은 아니다. 운영 상한은 연결·목록 조회 부하와 전체 메모리 측정으로 조정한다.

이 문서는 SSE 연결 관리의 구현 기준을 정의한다. 제품·서비스 정책과 별도로 `server/docs/sse/`에서 관리한다.

- 결정일: 2026-10-12.
- 임시 상한 재검토일: 2026-10-13. 10,000개 상향 검토 후 현재 자원 조건과 실측 범위를 근거로 기본값 1,000개를 선택했다.
- 대상: 기사 앱의 배송 요청 목록 SSE(Server-Sent Events: 열린 HTTP 연결로 서버 이벤트를 전달하는 방식), `GET /api/delivery/requests/events`.
- 적용 범위: 현재의 단일 서버. 여러 서버 사이의 기사별 연결 수 공유는 **비범위**다.
- 구현 상태: 기사 ID 전달, 연결 정보 관리, [연결 상한과 등록 및 정리 동시성 제어](#연결-상한과-등록-및-정리-동시성-제어), 초과 HTTP 응답을 구현했다. [실제 HTTP 연결의 거부와 정리 후 재등록](#실제-http-연결의-거부와-정리-후-재등록-검증)까지 검증했으며 관련 테스트 30개가 통과했다.

## 기사당 최대 2개 허용

- 같은 기사는 인증된 기사 ID로 구분한다. 같은 기기의 재접속과 다른 기기의 접속 모두 같은 기사의 연결 수에 포함한다.
- 서버에 등록되어 종료가 확인되지 않은 연결은 허용 개수에 포함한다. 앱이 연결을 닫았더라도 서버가 아직 종료를 확인하지 못했다면 포함한다.
- 동시 구독 요청에도 기사당 등록된 연결 수는 2개를 넘지 않아야 한다.
- 연결 종료가 확인되면 해당 연결이 차지한 허용 개수를 복구한다. 같은 연결의 종료 처리가 중복되어도 한 번만 복구한다.

두 연결을 항상 열어두는 정책은 아니다. 정상 연결이 1개인 동안에는 다른 연결을 추가로 만들 필요가 없다.

## 재접속 여유와 자원 비용

앱이 기존 연결을 닫고 새 연결을 시작해도 서버의 종료 확인은 늦을 수 있다. 기사당 1개만 허용하면 이 구간에서 재접속이 제한될 수 있다. 최대 2개를 허용하면 기존 연결 1개가 남아 있는 동안 새 연결 1개를 받을 수 있다.

이 선택은 두 기기의 동시 사용도 허용한다. 두 연결이 모두 사용되면 기사당 1개 제한보다 많은 연결 자원을 점유한다. 이미 2개가 등록된 상태에서 추가 재접속이 도착하는 상황까지 해소하지는 않는다. 실제 재접속 지연 완화 효과와 메모리 증가량은 **확인 필요**다.

## 유지할 동작

- 연결 타임아웃 120초와 heartbeat 간격 15초는 **유지**한다. 타임아웃은 연결 수명을 제한하며 동시 연결 개수의 상한을 대신하지 않는다.
- 커밋 후 목록 변경 신호 전달과 재접속 후 기존 목록 API를 통한 최신 상태 복구는 **유지**한다.
- 비동기 전송 구조 변경은 **보류**한다. 이번 정책의 범위는 연결 허용 개수다.

## 전체 연결 상한과 초과 응답

전체 연결 상한은 `setty.delivery.request-sse.max-connections`로 설정한다. 환경 변수 `SETTY_DELIVERY_REQUEST_SSE_MAX_CONNECTIONS`로 변경할 수 있으며 기본값은 임시 상한 1,000개다. 0 이하의 값은 서버 초기화 시 거부한다. 기본값은 [application.yml](../../src/main/resources/application.yml)에서 관리한다.

상한 설정만으로 해당 개수의 연결 객체를 미리 생성하지는 않는다. 실제 등록된 연결 수가 증가할 때 자원 점유가 늘어난다. [기존 로컬 부하 검증](sse-connection-load-local-2026-10-13.md)은 1,000개 연결·45초 유지·변경 이벤트 없음·빈 목록 조건이다. 운영 사양에서의 장시간 연결·목록 조회·재접속과 전체 메모리, FD·Tomcat·프록시 한도는 확인 필요다. 10,000개 운영 적용은 보류한다.

등록은 종료 상태, 전체 상한, 기사별 상한 순서로 검사한다. 전체 상한과 기사별 상한을 함께 초과하면 전체 상한 오류를 반환한다. 기존 연결을 교체하거나 종료하지 않고 신규 요청만 거부한다. 거부된 요청은 연결 맵에 등록하지 않으며 `connected` 이벤트를 보내지 않는다.

| 거부 이유 | HTTP 상태 | 응답의 `code` |
| --- | --- | --- |
| 기사당 2개 초과 | 429 | `DRIVER_LIMIT_EXCEEDED` |
| 전체 연결 상한 초과 | 503 | `TOTAL_LIMIT_EXCEEDED` |
| 연결 관리 종료 후 구독 | 503 | `STREAM_CLOSED` |

오류는 SSE 응답을 시작하기 전에 JSON으로 반환한다. `Accept: text/event-stream` 요청에서도 오류 응답의 `Content-Type`은 `application/json`으로 지정한다. 성공한 구독의 SSE 응답 형식은 유지한다.

서버의 `Retry-After` 안내와 앱의 재시도 조정은 **보류**한다. 앱은 기존 재접속 로직을 사용하며, 재시도 안내 반영과 무작위 지연은 별도 작업으로 검토한다.

## 기사 ID 전달과 연결 정보 관리

구독 API는 인증된 기사 ID를 `subscribe(DriverId)`에 전달한다. 연결마다 새 UUID를 만들고, 연결 맵의 값에 `DriverId`와 `SseEmitter`를 함께 저장한다. 기사별 구독 수는 이 맵에서 계산하며 별도 카운터는 두지 않는다.

같은 기사의 연결도 각각 등록하고, 변경 알림과 heartbeat는 기사 ID에 관계없이 모든 구독자에게 전송한다. 완료·타임아웃·오류·전송 실패는 UUID와 연결 정보를 함께 확인해 해당 항목만 제거한다. 같은 연결의 정리가 중복되어도 다른 연결에는 영향을 주지 않는다.

기사 ID 전달은 연결 상한 적용 전의 준비 단계다. 아래 10개 단위 테스트와 1개 통합 테스트 결과는 해당 준비 구현을 검증한 기록이다. 연결 상한 적용 후의 결과는 다음 절을 참조한다.

2026-10-12에 [구독 관리 단위 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestEventStreamTest.java) 10개와 [재접속 복구 통합 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestReconnectRecoveryIntegrationTest.java) 1개가 모두 통과했다. 실패·오류·건너뛴 테스트는 0개다. 실행 환경은 Java 21.0.5와 Testcontainers의 MySQL 8.4.11이다.

단위 테스트는 기사별 연결 구분, 전체 구독자 대상 변경 알림·heartbeat, 완료·타임아웃·오류·전송 실패의 개별 정리, 중복 콜백과 서버 종료를 검증했다. 복구 통합 테스트는 MockMvc의 구독 API로 인증된 기사 ID의 등록과 연결 종료 후 제거를 확인하고, SSE 단절 중 놓친 변경 이후의 최신 목록을 검사했다. 실제 네트워크 연결·부하·메모리 측정은 별도 검증이다.

## 연결 상한과 등록 및 정리 동시성 제어

`subscriptionsLock` 하나로 상한 검사와 등록, 개별 제거, 서버 종료, 정확한 구독 수 조회를 보호한다. 전체·기사별 연결 수는 `subscriptions` 맵에서 계산하며 별도 카운터를 두지 않는다. 기사별 연결 수 계산에는 등록된 전체 연결 수에 비례하는 순회 비용이 있다.

Emitter 생성과 종료 콜백 등록 후 잠금 안에서 상한을 검사하고 등록한다. `connected`, 목록 변경 알림과 heartbeat 전송은 잠금 밖에서 수행한다. 느린 전송이 다른 연결의 등록·정리를 막지 않게 하기 위한 경계이며, 전송 호출 자체의 비동기 격리는 아니다.

완료·타임아웃·오류·전송 실패는 같은 제거 메서드로 모은다. UUID와 연결 정보를 함께 확인해 제거하므로 중복 콜백은 다른 연결의 허용 개수를 복구하지 않는다. 제거된 항목이 맵에서 사라지는 것으로 해당 자리를 복구한다.

서버 종료는 잠금 안에서 종료 상태를 설정하고 종료할 연결을 복사한 뒤 맵을 비운다. 스케줄러 종료와 Emitter의 `complete()` 호출은 잠금 밖에서 수행한다. Emitter 생성 중 서버가 종료되더라도 이후 등록은 거부한다.

2026-10-12에 Java 21.0.5와 Testcontainers의 MySQL 8.4.11로 다음 검증을 실행했다. 총 27개가 통과했고 실패·오류·건너뛴 테스트는 0개다.

| 검증 | 결과와 범위 |
| --- | --- |
| [구독 관리 단위 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestEventStreamTest.java) 23개 | 기사별·전체 상한, 동시 구독 20건의 제한, 중복 정리 후 자리 복구, 느린 전송·종료 호출 중 등록·정리, 생성 중 서버 종료, 잘못된 전체 상한 설정을 검증했다. 전체 상한은 테스트값 3개를 사용했다. |
| [SSE API 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestSseControllerTest.java) 3개 | MockMvc에서 SSE Accept 헤더를 사용한 429·503 JSON 응답, 오류 응답 시 비동기 처리 미시작, 정리 후 재등록을 검증했다. 인증된 기사 정보는 모의로 제공했다. |
| [재접속 복구 통합 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestReconnectRecoveryIntegrationTest.java) 1개 | Spring 컨텍스트와 테스트 DB에서 인증된 구독 등록·정리 및 단절 중 놓친 변경 이후 최신 목록 복구를 확인했다. |

위 27개 테스트는 실제 TCP 연결 부하, 기사 기기의 재접속 시간, 서버 heap·프로세스 메모리 계측을 포함하지 않는다. 등록된 구독 수 제한과 운영 용량 검증은 구분한다.

## 실제 HTTP 연결의 거부와 정리 후 재등록 검증

2026-10-12에 [실제 HTTP 통합 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestSseHttpIntegrationTest.java) 3개와 기존 SSE 테스트 27개를 함께 실행했다. 총 30개가 통과했고 실패·오류·건너뛴 테스트는 0개다. 실행 환경은 macOS, Java 21.0.5, Testcontainers의 MySQL 8.4.11이다.

전체 Spring 애플리케이션을 임의의 로컬 포트로 기동하고 Java HTTP 클라이언트로 HTTP/1.1 요청을 보냈다. 격리 DB에 만든 기사 계정 4개의 Bearer 토큰으로 실제 인증 경로를 통과했다. 전체 연결 상한은 테스트값 3개를 사용하고 기사당 상한은 기존 정책을 유지했다. 서버 동작 코드는 수정하지 않았다.

| 시나리오 | 확인 결과 |
| --- | --- |
| 정상 구독과 목록 조회 | 200 SSE 응답과 `connected` 수신, 인증된 기사의 연결 등록, 목록 API의 200 배열 응답을 확인했다. |
| 기사별 상한 초과 | 같은 기사의 추가 연결은 429와 `DRIVER_LIMIT_EXCEEDED` JSON을 반환했다. 기존 연결 2개는 유지됐고 커밋 후 변경 알림을 모두 수신했다. 연결 하나를 닫고 서버 정리를 확인한 뒤 같은 기사가 재접속했다. |
| 전체 상한 초과 | 기사 3명의 연결 이후 다른 기사의 추가 연결은 503과 `TOTAL_LIMIT_EXCEEDED` JSON을 반환했다. 기존 연결 3개는 유지됐고 변경 알림을 모두 수신했다. 연결 하나를 닫고 서버 정리를 확인한 뒤 거부됐던 기사가 접속했다. |

거부 응답은 `Accept: text/event-stream` 요청에서도 `application/json`이었다. 5초 이내에 완결된 JSON 본문을 받았고 SSE의 `event:`·`data:` 필드가 없음을 확인했다. 거부 전후 등록된 연결 수는 같았다.

정리 검증에서는 클라이언트의 응답 스트림을 닫은 뒤 목록 변경 신호를 100ms 간격으로 발행했다. 서버가 실제 응답 쓰기에서 단절을 확인하고 구독 항목을 제거할 때까지 최대 5초 기다렸다. 종료 콜백이나 연결 맵을 테스트에서 직접 조작하지 않았다. 이 5초는 테스트의 대기 상한이며 운영 정리 시간의 측정값은 아니다. 변경이 없는 유휴 연결을 heartbeat만으로 정리하는 시간도 이번 검증에 포함하지 않았다.

429·503 이후 재등록 시나리오에서는 거부 이후 새 배송 요청을 실제 등록 서비스로 저장했다. 재접속한 기사 토큰으로 목록 API를 호출해 새 요청 ID가 응답에 포함되는지 확인했다. 테스트 종료 시 모든 HTTP 연결을 닫고 등록된 구독 수가 0으로 복구되는지도 확인했다.

실제 기사 앱의 자동 재시도·화면 반영, 운영 프록시, 대량 연결 부하와 메모리 계측은 **비범위**다. 로컬 HTTP 클라이언트의 재등록 성공과 기사 앱의 자동 복구는 구분한다.

Java 21과 Docker를 준비한 뒤 `server/`에서 실행한다.

```bash
./gradlew --no-daemon test \
  --tests setty.delivery.api.DeliveryRequestEventStreamTest \
  --tests setty.delivery.api.DeliveryRequestSseControllerTest \
  --tests setty.delivery.api.DeliveryRequestReconnectRecoveryIntegrationTest \
  --tests setty.delivery.api.DeliveryRequestSseHttpIntegrationTest
```

## 구독 제거와 HTTP 완료 로그

**채택: 기존 `SseEmitter.onCompletion`에서 완료 로그를 남긴다.** [구독 제거 로그](../../src/main/java/setty/delivery/api/DeliveryRequestEventStream.java)와 [HTTP 완료 로그](../../src/main/java/setty/global/logging/SseCompletionLogging.java)를 같은 `subscription_id`·`request_id`로 연결한다. 오류·타임아웃으로 구독이 먼저 제거돼도 이후 완료 콜백의 로그는 남는다.

같은 서버 실행에서 `completed_at_nanos - removed_at_nanos`를 1,000,000으로 나누면 제거부터 완료 콜백까지의 밀리초를 얻는다. `*_at_epoch_ms`는 로그 시각 대조에 사용한다. 정상 완료에서는 완료 콜백 안에서 구독을 제거하므로 완료 시각이 제거 시각보다 앞설 수 있다. 이를 종료 지연으로 집계하지 않는다.

별도 HTTP 요청 상태, 필터, 옵저버, 메트릭은 추가하지 않는다. 미완료 HTTP 요청 수의 독립 집계는 **보류**하며, 완료 콜백은 클라이언트나 TCP 연결의 정리 완료를 뜻하지 않는다. 검증은 [기존 로그 단위 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestEventStreamTest.java)와 [실제 HTTP 테스트](../../src/test/java/setty/delivery/api/DeliveryRequestSseHttpIntegrationTest.java)를 사용한다.

## 인증 조회 계측과 자원 대조

**채택: 각 모듈의 인증 저장소 호출을 타이머 하나로 측정한다.** [회원 인증](../../src/main/java/setty/platform/member/controller/MemberAuthInterceptor.java)과 [배송원 인증](../../src/main/java/setty/delivery/auth/api/DeliveryAuthInterceptor.java)의 `setty.auth.repository.lookup`은 실제 조회의 횟수·시간을 기록한다. 태그는 `repository`(`member`, `delivery_member`)와 `dispatcher`(Servlet 디스패치 종류)만 남긴다. `REQUEST`·`ASYNC`·`ERROR`를 구분하며 요청 ID·기사 ID·토큰은 제외한다. 모듈별 인증 경로와 인증 판단은 유지한다.

[허용 목록](../../src/main/java/setty/global/metrics/MetricsConfig.java)과 [dev](../../src/main/resources/application-dev.yml)·[prod](../../src/main/resources/application-prod.yml) 설정으로 노출한다. 타이머는 첫 실제 조회 시 등록한다. SSE를 포함한 인터셉터 전체 조회를 합산하며 다른 경로의 직접 조회는 포함하지 않는다. 횟수·총시간은 누적값이고 평균 시간은 같은 수집 구간의 `총시간 증가량 / 횟수 증가량`이다. 횟수 증가가 0이면 평균을 계산하지 않는다. 타이머의 의미는 [Micrometer 문서](https://docs.micrometer.io/micrometer/reference/concepts/timers.html)를 참조한다.

실측에서는 기존 1분 수집 구간에 맞춰 인증 조회 횟수·시간, Hikari 대기·획득 시간, Heap과 RSS를 대조한다. RSS는 [기존 부하 스크립트](performance/README.md)의 `samples.jsonl`에서 `at`과 `resources.rssBytes`를 사용한다. dev RSS는 dev 서버에서 수집한다. 지표의 누적 횟수·총시간은 0으로 돌아오지 않으며, 구독 수와 Hikari 활성·대기 수의 기준값 복귀를 확인한다. 실제 정상 종료·연결 끊김·타임아웃 시험과 원인 판정은 후속 검증에서 수행한다.

## 후속 검증 기준

1. 실제 HTTP 연결에서 이전 연결의 종료 확인이 늦는 구간에 새 연결을 허용하고, 정리 후 다시 등록할 수 있다.
2. 실제 앱이 429·503 응답 후 재접속하고 최신 목록을 복구한다. 반복 거부 시 재접속 부하도 기록한다.
3. 운영 예상 동시 연결 수, 서버 heap·프로세스 메모리, 프록시 설정을 확인해 전체 상한을 조정한다.

다음 행동은 실제 앱에서 상한 초과 거부 이후 연결 정리와 재접속·목록 복구를 확인하는 것이다.

## 구현 및 검증 자료

- [서버 연결 등록·정리와 전송](../../src/main/java/setty/delivery/api/DeliveryRequestEventStream.java)
- [기사 인증과 SSE 구독 API](../../src/main/java/setty/delivery/api/DeliveryController.java)
- [앱 연결 종료·재접속과 목록 재조회](../../../apps/driver/src/api/deliveryRequestEvents.ts)
- [기존 재접속 복구 검증 결과](delivery-request-sse-recovery.md)
