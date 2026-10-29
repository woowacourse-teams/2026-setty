# 배포 중 중단된 이벤트의 주기적 복구

**채택: EVENT_PUBLICATION에 남은 중단 기록을 주기적으로 실패로 전환하고 기존 재발행 스케줄러로 회수한다.** 신규 JVM 기동 시 미완료 전체를 재발행하는 설정은 끈다. 구현은 Spring Modulith 2.1.2의 staleness 감시를 사용하며 별도 아웃박스 테이블이나 메시지 브로커를 추가하지 않는다.

## 복구 흐름

```text
업무 변경과 이벤트 발행 기록 커밋
  → 비동기 리스너 실행
  → JVM 종료 등으로 PUBLISHED / PROCESSING / RESUBMITTED 기록이 남음
  → staleness 감시가 오래된 기록을 FAILED로 전환
  → FailedEventResubmissionScheduler가 재발행
  → 성공하면 completion-mode: delete에 따라 발행 기록 삭제
```

Green 기동 이후 Blue에서 발생한 이벤트도 같은 DB의 발행 기록으로 복구한다. 살아 있는 JVM에서 감시와 재시도가 실행되므로, 복구를 위해 추가 재시작이 필요하지 않다. 모든 JVM이 중지된 동안에는 복구도 실행되지 않는다.

시작 시 미완료 전체 재발행은 다른 JVM이 처리 중인 작업까지 다시 가져갈 수 있어 비활성화한다. 배포 훅의 TCP 연결 종료 대기는 이벤트 작업의 종료를 뜻하지 않는다. 배포 절차는 [배포 문서](../../deploy/README.md)를 참조한다.

## 설정과 복구 지연

설정의 단일 기준은 [application.yml](../../src/main/resources/application.yml)이다. 아래 값은 운영 측정으로 확정한 성능 목표가 아닌 초기 기본값이다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SETTY_EVENT_STALENESS_PUBLISHED` | `PT5M` | 최초 처리를 기다리는 기록의 감시 기준 |
| `SETTY_EVENT_STALENESS_PROCESSING` | `PT5M` | 처리 중 남은 기록의 감시 기준 |
| `SETTY_EVENT_STALENESS_RESUBMITTED` | `PT5M` | 재발행 후 남은 기록의 감시 기준 |
| `SETTY_EVENT_STALENESS_CHECK_INTERVAL` | `PT30S` | 오래된 기록을 실패로 전환하는 검사 주기 |
| `SETTY_EVENT_RESUBMISSION_INTERVAL` | `PT1M` | 실패 기록을 다시 발행하는 주기 |

기간은 양수로 지정한다. 2.1.2의 설정 바인딩은 명시한 0 또는 음수의 staleness 기간을 거부한다. 상태 설정을 생략했을 때의 라이브러리 기본 동작과 명시적으로 `PT0S`를 전달하는 것은 다르다.

오래된 기록인지 판단할 때는 최초 발행 시각 또는 마지막 재발행 시각을 사용한다. `PROCESSING`으로 바뀐 시각부터 별도의 타이머가 시작되는 방식이 아니므로 큐 대기시간도 함께 고려한다. 재발행한 작업은 마지막 재발행 시각을 기준으로 판단해 즉시 오래된 작업으로 다시 분류하지 않는다.

최종 복구 지연에는 감시 기준, 검사 주기, 재발행 주기, 큐 대기와 실제 처리시간이 함께 영향을 준다. 기본값만으로 5분 안에 복구된다고 보장하지 않는다. 정상 작업이 기준보다 오래 걸리면 중단으로 오인해 동시에 다시 실행할 수 있으므로, 정상 대기와 실행시간을 측정한 뒤 값을 조정한다.

공통 및 슬롯 환경 파일에 `SPRING_MODULITH_EVENTS_REPUBLISH_OUTSTANDING_EVENTS_ON_RESTART=true` 같은 덮어쓰기가 남아 있지 않은지 적용 전에 확인한다.

## 재시도 한도와 멱등성

[기존 스케줄러](../../src/main/java/setty/global/event/FailedEventResubmissionScheduler.java)는 최초 처리를 포함해 시도 횟수가 5회 미만인 실패 건을 다시 발행한다. 한도를 소진한 건은 `FAILED`로 남으며 무제한 재시도하지 않는다. 원인을 해결한 뒤 해당 업무 상태와 멱등성을 확인해 후속 처리한다.

재발행은 중복 실행 가능성을 전제로 한다. 같은 이벤트를 순차 또는 동시에 받아도 업무 결과가 중복되지 않도록 리스너의 행 잠금, 상태 검사와 유일성 제약을 유지해야 한다. DB 발행 기록만으로 외부 푸시 전송 같은 부수 효과까지 정확히 한 번 수행된다고 보장하지 않는다.

미완료 건을 확인하는 읽기 전용 SQL은 다음과 같다. 완료된 기록은 삭제되므로 이 테이블로 전체 처리 이력을 조회할 수는 없다.

```sql
SELECT STATUS, COUNT(*) AS publication_count,
       MIN(PUBLICATION_DATE) AS oldest_publication_date
FROM EVENT_PUBLICATION
WHERE COMPLETION_DATE IS NULL
GROUP BY STATUS;

SELECT ID, LISTENER_ID, EVENT_TYPE, STATUS, COMPLETION_ATTEMPTS,
       PUBLICATION_DATE, LAST_RESUBMISSION_DATE
FROM EVENT_PUBLICATION
WHERE STATUS = 'FAILED' AND COMPLETION_ATTEMPTS >= 5
ORDER BY PUBLICATION_DATE
LIMIT 100;
```

## 결제와 주문 만료의 적용 한계

**이번 변경은 결제 완료 후 주문이 복구 전에 만료되는 경합을 해결하지 않는다.** 주문 만료 시각이 가까울 때 결제가 완료되고 이벤트 처리가 중단되면, 재발행보다 만료 처리가 먼저 실행될 수 있다. 감시 시간을 주문 만료 기본값인 6분보다 짧게 정하는 것만으로는 막을 수 없다.

이미 만료된 주문에 결제 완료가 도착하는 경우의 동작과 운영 절차는 [만료 주문 수동 환불 문서](../payment/expired-order-manual-refund.md)를 따른다. 결제된 PENDING 주문의 만료 보호, 자동 환불과 PG 승인 후 로컬 저장 이전의 중단 대사는 별도 범위다.

## 구현 확인과 남은 검증

- [시간 판정 회귀 테스트](../../src/test/java/setty/global/event/EventPublicationStalenessTest.java): 중단된 세 상태의 실패 전환, 최근 재발행 보호, 미설정 상태의 감시 제외를 검증한다.
- [설정 테스트](../../src/test/java/setty/global/event/EventRecoveryConfigurationTest.java): 기본, dev, prod의 감시 활성화와 시작 전체 재발행 해제, 환경변수 조정과 잘못된 기간의 기동 실패를 검증한다.
- [MySQL 통합 테스트](../../src/test/java/setty/global/event/FailedEventResubmissionIntegrationTest.java): 실제 발행 기록에 중단 상태와 경과시간을 설정한 뒤 실패 전환과 재발행으로 업무 상태가 반영되는지 검증한다. 실제 JVM 종료를 모사하는 테스트는 아니다.

2026-10-29 전체 서버 테스트는 798건 중 797건이 통과했고 외부 FCM 측정 1건은 실행 조건 미충족으로 건너뛰었다. MySQL 중단 복구 통합 테스트 4건도 이 결과에 포함된다. 배포 스크립트 테스트 30건, 셸 구문 검사와 클라이언트 타입 검사도 통과했다.

실제 JAR 두 개와 격리 MySQL을 사용하는 [재현 도구](../../event-recovery-lab/README.md)로 동일 버전과 기존 기준 버전 Blue 및 신규 Green 조합을 확인했다. 결제 리스너와 배송 리스너의 SIGKILL, 서로 다른 JVM의 리스너 중첩, 동일 이벤트 재전달과 재시도 소진이 모두 기대 결과를 만족했다. 회차별 JAR 해시, PID, 상태와 시간은 [검증 결과](../../event-recovery-lab/results/2026-10-29.json)에 보존한다.

실험은 감시 기준 15초, 검사와 재발행 주기 각 1초를 사용했다. 운영 기본 설정의 복구 지연, Nginx와 systemd 및 AWS 환경의 배포 동작, 주문 만료 경합과 외부 부수 효과는 이 결과로 보장하지 않는다. PR 생성 후 원격 CI와 리뷰를 확인하고, 배포 환경별 설정은 별도로 검증한다.

## 구현 근거

- [Spring Modulith 이벤트 레지스트리와 staleness](https://docs.spring.io/spring-modulith/reference/events.html)
- [2.1.2 레지스트리의 마지막 발행 시각 판정](https://github.com/spring-projects/spring-modulith/blob/2.1.2/spring-modulith-events/spring-modulith-events-core/src/main/java/org/springframework/modulith/events/core/DefaultEventPublicationRegistry.java)
- [2.1.2 릴리스 안내](https://spring.io/blog/2026/10/23/spring-modulith-2-2-rc1-2-1-2-2-0-9-and-1-4-14-released/)
