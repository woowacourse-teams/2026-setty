# ListingCreated

## 발생

- 발행자: [`ListingService#create`](../../src/main/java/setty/platform/listing/application/ListingService.java)
- 상황: 매물이 최초 등록되어 매물·사진이 저장된 직후, 같은 트랜잭션 안에서 발행한다. `update`·삭제·상태 변경에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/ListingCreated.java)의 매물 ID, 판매자 ID, 제목, 등록 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`ListingNotificationCreationListener#onListingCreated`](../../src/main/java/setty/notification/listing/application/ListingNotificationCreationListener.java) | `@EventListener`로 발행 트랜잭션 안에서 실행한다. 제목에 키워드가 포함된 구독자(판매자 제외)마다 `PENDING` 알림을 1건 만든다. 등록이 롤백되면 알림도 남지 않는다. |
| [`ListingNotificationDispatcher#onListingCreated`](../../src/main/java/setty/notification/listing/application/ListingNotificationDispatcher.java) | `@ApplicationModuleListener`로 등록 커밋 뒤 비동기로 실행한다. 해당 매물의 `PENDING` 알림을 500건씩 묶어 전용 스레드 풀에서 병렬 발송하고 결과를 `SENT` 또는 시도 횟수 증가(3회 실패 시 `FAILED`)로 기록한다. 재시도 대상이 남으면 예외를 던져 `FailedEventResubmissionScheduler`가 재발행한다. 재발행돼도 `PENDING`만 집으므로 중복 발송은 없다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
