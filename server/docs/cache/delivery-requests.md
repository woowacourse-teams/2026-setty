# 배송 대기 목록 캐시

[`AvailableRequestCache`](../../src/main/java/setty/delivery/application/AvailableRequestCache.java)는 `GET /api/delivery/requests`의 전체 배열과 커서 없는 첫 페이지를 서버 메모리(Caffeine)에 둔다. SSE 알림을 받은 배송원들이 같은 목록을 한꺼번에 다시 조회할 때 DB를 반복해 읽지 않기 위해서다. 커서 있는 다음 페이지와 상세는 캐시하지 않는다.

## 무효화 규칙

| 시점 | 처리 |
| --- | --- |
| `DeliveryRequestsChanged` 커밋 후(`AFTER_COMMIT`) | 세대 번호를 올리고 모두 비운다. `@Order(HIGHEST_PRECEDENCE)`로 SSE 알림보다 먼저 실행한다. |
| 쓰기 후 30초 | 만료. 이벤트를 거치지 않는 변경(수동 SQL, 매물 사진 교체 등)은 최대 이만큼 늦게 보인다. |
| 서버 시작·배포 | 빈 캐시로 시작한다. |

- 이벤트는 등록·수락·배정 취소·`REQUESTED` 주문 취소에서 발행한다. 픽업·완료는 대기 목록에 영향이 없다.
- 키에 세대 번호를 넣어, 변경 커밋 전에 시작한 조회가 무효화 뒤에 끝나도 그 결과가 새 요청에 쓰이지 않게 한다.
- 같은 키를 동시에 조회하면 한 요청만 DB를 읽고 나머지는 그 결과를 기다린다.

## 설정

| 키 | 기본값 |
| --- | --- |
| `setty.delivery.request-cache.enabled` (`SETTY_DELIVERY_REQUEST_CACHE_ENABLED`) | `true`. 테스트 설정은 `false` |
| `setty.delivery.request-cache.ttl` (`SETTY_DELIVERY_REQUEST_CACHE_TTL`) | `PT30S` |
| `setty.delivery.request-cache.maximum-size` | `16` |

## 확장

지금은 EC2 1대에서 Blue/Green을 전환하므로 로컬 캐시로 충분하다. 서버가 여러 대가 되면 값은 로컬에 두고, SSE fan-out과 같은 Redis Pub/Sub 채널로 무효화 신호만 전달한다. 신호가 유실되면 TTL로 복구한다.
