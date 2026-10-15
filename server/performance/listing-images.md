# 사진 수 변경과 목록 조회 비교

전체 매물 수를 고정하고 실험 매물 5,000건의 사진 메타데이터만 1→5→1개로 바꾼다. 핵심 로컬 쿼리 비교는 [별도 재현 도구](local-thumbnail-query/README.md)를 사용한다. 이 문서는 별도 DB/API 환경에서 사진 수 증가 영향을 확인하는 수동 절차다. 과거 dev의 측정값은 [선별 결과](local-thumbnail-query/README.md#결과와-선택)에 보존했다.

## 실행 조건

기존 [fixture 준비 절차](README.md#데이터-준비와-정리)로 만든 전용 계정·매물 5,000건이 필요하다. `set-listing-images-5000.sql`은 매물을 생성하지 않는다. 기존 fixture의 제목·필드·생성 시각·사진 키를 확인하므로 새로운 환경에서 데이터가 다르면 즉시 중단한다. 이 SQL의 확인 조건을 삭제해 다른 데이터에 강제로 실행하지 않는다.

- 전용 사진 5,000행을 기준으로 20,000행을 추가하고, 같은 키·순서의 20,000행만 제거한다. 대표 사진·매물·다른 판매자의 데이터는 유지한다.
- 실제 이미지 파일이나 S3 객체는 만들지 않는다. 영구 스키마·인덱스도 바꾸지 않는다.
- 배포와 데이터가 고정된 실험 환경에서 DB명·호스트·전체 AVAILABLE 수를 먼저 읽기 확인한다. 공유 환경의 데이터 변경은 담당자와 조율한다.
- 기본 SQL은 ROLLBACK이다. 배치 연결에 `--skip-force --skip-reconnect`를 적용해 오류 후 실행을 이어가지 않는다.

## 사진 1→5개 준비와 5→1개 복원

`server/performance/`에서 실행하는 명령 예시다. 대문자 자리표시는 확인한 값으로 바꾸고 비밀번호는 프롬프트에 입력한다.

```bash
fixture_init="SET @fixture_expected_database='DB_NAME', @fixture_expected_hostname='MYSQL_HOSTNAME', @fixture_expected_available_listings=AVAILABLE_COUNT, @fixture_images_before=1, @fixture_images_after=5;"
mysql --batch --skip-reconnect --skip-force --init-command="$fixture_init" \
  -u DB_USER -p DB_NAME < fixtures/set-listing-images-5000.sql
```

롤백 시험에서 변경 중 전용 매물 5,000·사진 25,000행, 롤백 후 사진 5,000행과 전체 목록 수 유지를 확인한다. 저장하려면 원본을 유지하고 임시 실행본의 단독 `ROLLBACK;` 한 줄만 `COMMIT;`으로 바꿔 실행한다. 사진 5→1개 복원은 `@fixture_images_before=5`, `@fixture_images_after=1`로 바꾸어 같은 롤백 시험과 저장 절차를 거친다.

조건 전환 전후에 전체 목록 ID·순서·대표 사진 URL과 원래 매물·사진 행 해시를 확인한다. 해시 계산은 비교 범위와 컬럼·정렬 순서를 고정해야 한다. 복원은 행 내용의 복원이며 AUTO_INCREMENT 값을 되돌린다는 의미가 아니다.

## 목록 측정

`server/`에서 기존 `ListingCountSimulation`을 사용한다. `LISTING_COUNT`는 이미 준비된 **전체** 응답 매물 수이며 DB를 변경하지 않는다. 과거 실험은 5,020건, 최종 dev는 5,138건이었다. 시나리오는 100·1,000·5,000·5,020·5,138건을 허용한다. 현재 환경의 수가 다르면 다른 데이터를 삭제하지 말고 별도 실험 조건으로 정리한다.

```bash
LISTING_COUNT=5138 LISTING_BASE_URL=https://www.setty.cloud ./gradlew gatlingRun \
  --simulation setty.performance.ListingCountSimulation \
  --run-description listings-5138-photos-1-run-1
```

위 URL은 당시 dev 실험 대상이었다. 재실행 전 현재 라우팅·대상 환경을 확인하고, localhost 검증에는 `http://127.0.0.1:PORT`를 사용한다. 이 문서의 명령을 수행하는 것과 과거 데이터의 오프라인 재검산은 별개다.

순차 사용자 1명, 응답 뒤 1초 대기, 워밍업 20건+본 측정 100건으로 사진 1개 3회 → 5개 3회 → 복원 1회를 개별 실행한다. 마지막 응답 후 최소 60초 쉬고 결과를 확인한다. 5,020·5,138건 조건은 요청 ID 누락·응답 5초 이상을 실패로 처리한다. HTTP 오류·응답 건수 불일치·배포 변경·데이터 변경·지속적인 DB 대기 시 다음 회차를 진행하지 않는다. 5초는 당시 탐색 중단선이며 목표 SLA가 아니다.

매 회차 120건 성공, 본 측정 100건인지 확인하고 CSV의 요청 ID를 서버 `listing_request_timing` 로그에 연결한다. `status=200`, `outcome=completed`, 응답·세 구간 완료, 목록·사진·요약 수 일치를 확인한다. `images_ms`·`filter_ms`·클라이언트 시간은 본 측정만 집계한다. 실패·중단 회차는 정상 통계에 섞지 않는다. 로그 수집과 해석은 [요청 계측 안내](request-timing.md)를 따른다.

회차별 코드 버전·환경·UTC 시간·CSV·서버 원본·집계를 보존하고 마지막에 정확한 추가 사진만 제거한다. 서버 원본을 보존하지 못한 경우 최종 dev 기록처럼 집계만 있다는 한계를 명시한다. 조건 순서를 교차하지 않아 시간대·캐시 영향이 남으며 최대 동시 처리량 시험으로 해석하지 않는다.
