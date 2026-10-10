# ListingCreated

## 발생

- 발행자: [`ListingService#create`](../../src/main/java/setty/platform/listing/application/ListingService.java)
- 상황: 매물이 최초 등록되어 매물·사진이 저장된 직후, 같은 트랜잭션 안에서 발행한다. `update`·삭제·상태 변경에는 발행하지 않는다.
- 전달 값: [이벤트 클래스](../../src/main/java/setty/common/ListingCreated.java)의 매물 ID, 판매자 ID, 제목, 등록 시각.

## Fanout

| 수신자 | 처리 |
| --- | --- |
| [`ListingNotificationCreationListener#onListingCreated`](../../src/main/java/setty/notification/listing/application/ListingNotificationCreationListener.java) | `@EventListener`로 발행 트랜잭션 안에서 실행한다. 제목에 키워드가 포함된 구독자(판매자 제외)마다 `PENDING` 알림을 1건 만든다. 등록이 롤백되면 알림도 남지 않는다. |

## 다음 확인할 이벤트 문서

- 현재 코드에서 확인한 후속 이벤트 문서 없음.
