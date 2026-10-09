# 목록 조회 요청 계측

목적은 느린 Gatling 요청 하나를 서버 로그와 연결하고, 목록 조회 내부의 지연 구간을 좁히는 것이다. 페이지 처리나 쿼리 최적화는 포함하지 않는다.

## 적용 범위

- `dev` 프로필 + `setty.observability.listing-timing.enabled=true`일 때만 활성화한다. dev 설정의 기본값은 true이며, `SETTY_OBSERVABILITY_LISTING_TIMING_ENABLED=false`로 끌 수 있다. 기본/prod 프로필에서는 등록되지 않는다.
- 앱 경로 `GET /api/listings`의 최초 REQUEST 디스패치만 계측한다. 외부 `/dev/api/listings`의 `/dev`는 ALB에서 제거된다. 다른 API와 SSE는 제외한다.
- 요청 ID 필터 안쪽에서 시작해 동기 필터 체인이 반환한 직후 **요청당 INFO 로그 한 줄**을 출력한다. 기존 JSON 로그에 MDC의 `requestId`와 아래 필드가 들어간다.
- 요청·응답 본문, 쿼리 문자열, 인증 헤더, 예외 메시지는 추가 로그에 담지 않는다. 응답을 버퍼링하거나 복사하지 않는다.

## 필드와 측정 경계

| 필드 | 의미 |
| --- | --- |
| `event` | `listing_request_timing` |
| `method`, `path`, `status` | GET, /api/listings, 필터 반환 시 상태 코드. 최종 상태를 알 수 없는 예외·비동기 전환은 status=null |
| `filter_ms` | 필터 진입부터 체인 반환/예외까지의 경과 시간. 아래 구간 외에 트랜잭션, MVC, JSON 변환과 응답 쓰기를 포함하지만 로그 출력 자체와 이후 컨테이너 flush, 클라이언트 수신 완료는 제외 |
| `listings_ms` | 매물 repository 호출. SQL 실행뿐 아니라 결과 수신·엔티티 생성도 포함 |
| `images_ms` | ID 목록 생성, 이미지 repository 조회, 대표 사진 Map 구성 전체 |
| `mapping_ms` | URL 문자열과 응답 객체 목록 생성. JSON 직렬화는 포함하지 않음 |
| `listing_count`, `image_count`, `summary_count` | 실제 반환받은 매물·이미지 엔티티와 생성한 요약 개수. DB 내부 스캔 행 수나 SQL 횟수가 아님 |
| `*_completed` | 해당 구간 정상 반환 여부. 실행되지 않은 구간·확인하지 못한 개수는 필드 자체가 없음 |
| `outcome`, `response_complete` | `completed`/true는 동기 체인 반환. 처리된 4xx·5xx도 포함하며 HTTP 성공 여부는 status로 판단 |

`send_error`/false는 컨테이너 오류 페이지 처리 전, `exception`/false는 처리되지 않은 예외 전파, `async_started`/false는 비동기 전환 시점의 부분 기록이다. ERROR·ASYNC 재디스패치를 다시 세지 않으며, 현재 동기 목록 API를 비동기로 변경한다면 완료 계측도 함께 변경해야 한다. `exception_type`에는 예외 클래스 이름만 남긴다.

시간은 `System.nanoTime()`으로 계산한다. 필터 시간에서 세 구간 합을 뺀 값에는 여러 처리 비용이 섞이므로 **JSON 직렬화 시간으로 단정하지 않는다**. Gatling 시간과 필터 시간의 차이도 순수 네트워크 시간으로 단정하지 않는다. 계측 자체 비용이 추가되므로 이후 개선 전후 비교에서는 같은 계측 설정을 유지한다.

## 로컬 검증

`server/`에서 실행한다. 첫 명령은 DB가 필요 없는 실제 HTTP·필터·서비스 테스트이고, 두 번째는 약 2분간 localhost 임시 서버만 사용하는 Gatling 검증이다.

```bash
./gradlew test --tests 'setty.global.logging.*' --tests 'setty.platform.listing.application.ListingServiceTest' compileGatlingJava
python3 performance/verify-request-id.py
```

HTTP 테스트는 실제 컨트롤러·서비스·JSON 변환을 사용하고 repository만 대체한다. 쿼리 실행 시간이나 dev 성능 검증을 의미하지 않는다. MySQL을 포함한 전체 회귀 테스트는 Docker 환경에서 `./gradlew test`로 실행한다.

## dev 배포 후 확인 순서

1. 배포 커밋과 dev 프로필·활성화 설정을 확인한다. 단건 스모크로 `listing_request_timing` 로그가 수집되는지 먼저 확인한다.
2. 기존 조건 그대로 매물 5,000개, 사진 메타데이터 각 1개, 사용자 1명, 워밍업 20회·본 측정 100회, 응답 후 1초 대기로 실행한다. 계측 보완 전 CSV·HTML은 보존한다.
3. 새 CSV의 `phase=listings-measurement`에서 느린 요청의 `request_id`와 `completed_at`을 고른다. CSV에는 서버 응답 헤더 `X-Request-Id`를 그대로 저장하며, 헤더가 없으면 빈 칸이다. **누락을 HTTP 실패로 바꾸지는 않지만 로그 연결 검증은 미완료**로 판단한다. 직전 요청의 ID를 재사용하지 않는다.
4. CloudWatch Logs Insights에서 `/setty/dev/backend`를 선택하고 해당 요청 전후의 시간 범위를 지정한 뒤 아래 쿼리를 실행한다. `REQUEST_ID_FROM_CSV`를 실제 값으로 바꾼다. 현재 수집 구조인 `event.body.MESSAGE` 안의 앱 JSON을 파싱한다.

```text
fields jsonParse(@message) as envelope
| fields jsonParse(envelope.body.MESSAGE) as app
| filter app.requestId = "REQUEST_ID_FROM_CSV"
| filter app.event = "listing_request_timing"
| fields @timestamp, app.requestId, app.status, app.outcome, app.response_complete,
    app.filter_ms, app.listings_ms, app.images_ms, app.mapping_ms,
    app.listings_completed, app.images_completed, app.mapping_completed,
    app.listing_count, app.image_count, app.summary_count
| sort @timestamp asc
| limit 100
```

완료 기준은 CSV 요청 ID와 서버 로그가 일치하고 정상 목록 응답의 세 구간·개수를 확인하는 것이다. 로그가 없으면 시간대·로그 수집·배포 버전·활성화 설정을 먼저 확인한다. 필터 p95가 작다는 사실만으로 개별 요청의 긴 서버 처리를 배제하지 않는다. 이전 16초 요청에는 ID 기록이 없으므로 새 계측으로 소급해 연결할 수 없다.
