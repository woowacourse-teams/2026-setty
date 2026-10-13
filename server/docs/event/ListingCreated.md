# ListingCreated

## 발생

- 발행자: [`ListingRecorder#create`](../../src/main/java/setty/platform/listing/application/ListingRecorder.java)
- 상황: 매물이 최초 등록되어 매물·사진이 저장된 직후, 같은 트랜잭션 안에서 발행한다. 사진 업로드는 이 트랜잭션 밖에서 먼저 끝난다. `update`·삭제·상태 변경에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/ListingCreated.java)의 매물 ID, 판매자 ID, 제목, 등록 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`ListingNotificationDispatcher#onListingCreated`](../../src/main/java/setty/notification/listing/application/ListingNotificationDispatcher.java) | `@Async(notificationDispatchExecutor)` + `@TransactionalEventListener(AFTER_COMMIT)`로 등록 커밋 뒤 전용 풀에서 실행한다. 먼저 제목에 키워드가 포함된 구독자(판매자 제외)마다 `PENDING` 알림을 1건 만든다. 같은 매물·구독자 알림이 이미 있으면 건너뛰므로 다시 실행돼도 중복되지 않는다. 이어서 `PENDING` 알림을 500건씩 묶어 발송 스레드 풀에서 병렬 발송하고 결과를 `SENT` 또는 시도 횟수 증가(3회 실패 시 `FAILED`)로 기록한다. 재시도 대상이 남으면 예외를 던져 `FailedEventResubmissionScheduler`가 재발행한다. 실제 전송은 `NotificationSender` 구현체가 맡는다(`setty.notification.sender`: `noop` 기본, `mock` 측정용, `fcm`은 `device_tokens`의 기기로 Firebase 발송). |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
