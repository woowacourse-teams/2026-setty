# GA4 사용자 행동 모니터링

## 1. 이유 확인

배포된 서비스의 방문자 수, 유입 경로와 페이지별 이용 현황을 확인할 수단이 없었다. 기능 개선 우선순위를 정할 때 팀의 가설을 실제 사용자 행동과 비교하기 위해 GA4를 선택했다.

현재 가장 먼저 확인할 질문은 다음과 같다.

- 몇 명이 서비스를 방문하는가?
- 방문자는 어떤 경로와 기기로 들어오는가?
- 노출된 매물 중 무엇을 넘기고 자세히 확인하는가?
- 문의와 매물 등록을 시작한 사용자가 실제 완료하는가?
- 가격 구간에 따라 상세 조회와 문의 행동에 차이가 있는가?

GA4는 방문자·세션·유입 분석을 기본 제공하므로 방문 현황을 먼저 파악하려는 목적에 맞다. 기능별 퍼널 분석이 더 중요해지면 이벤트를 추가하거나 제품 분석 도구 도입을 재검토한다.

## 2. 적용 범위

`SETTY_GA_MEASUREMENT_ID`가 설정된 빌드에서만 Google 태그를 불러온다. 값이 없는 로컬·테스트 환경에서는 수집하지 않는다. 별도 npm 패키지는 추가하지 않았다.

| 이벤트 | 수집 시점 | 확인하려는 질문 |
| --- | --- | --- |
| `page_view` | 첫 진입과 React Router 경로 변경 | 어느 페이지를 얼마나 방문하는가? |
| `listing_card_impression` | 현재 매물 카드가 화면에 노출 | 사용자가 매물을 몇 개 확인하는가? |
| `listing_skipped` | 넘기기 버튼 또는 왼쪽 스와이프로 다음 카드 이동 | 어떤 매물을 넘기는가? |
| `listing_detail_opened` | 카드 탭, 상세 버튼, 오른쪽 스와이프로 상세 진입 | 방문자가 매물을 자세히 살펴보는가? |
| `message_compose_opened` | 유효한 매물의 쪽지 작성 화면 진입 | 문의를 시작하는가? |
| `message_sent` | 쪽지 API가 성공한 직후 | 실제 문의 행동으로 이어지는가? |
| `listing_create_started` | 신규 등록 폼의 첫 입력 또는 사진 선택 | 판매 등록을 시작하는가? |
| `listing_created` | 신규 매물 등록 API가 성공한 직후 | 실제 등록을 완료하는가? |

매물 관련 이벤트에는 숫자 ID(`listing_id`)와 가격 구간(`price_bucket`)을 기록한다. 상세 진입 방식(`detail_open_method`)은 `card_tap`, `detail_button`, `swipe_right`, 넘김 방식(`skip_method`)은 `skip_button`, `swipe_left` 중 하나다.

가격 원문은 보내지 않고 다음 구간만 사용한다.

| `price_bucket` | 가격 |
| --- | --- |
| `unavailable` | 가격 없음 또는 유효하지 않은 값 |
| `zero` | 0원 |
| `under_50k` | 1원 이상 5만 원 미만 |
| `50k_to_100k` | 5만 원 이상 10만 원 미만 |
| `100k_to_300k` | 10만 원 이상 30만 원 미만 |
| `over_300k` | 30만 원 이상 |

매물 제목·설명·사진 URL, 픽업 정보 원문, 전화번호, 비밀번호와 쪽지 내용은 GA에 보내지 않는다.

### 관찰 흐름

```text
방문 → 페이지 조회 → 카드 노출 → 넘김 또는 상세 조회 → 문의 작성 → 문의 전송
방문 → 등록 페이지 → 등록 작성 시작 → 매물 등록 성공
```

다음 비율을 같은 관찰 기간 안에서 비교한다.

- 상세 조회율 = `listing_detail_opened` / `listing_card_impression`
- 넘김 비율 = `listing_skipped` / `listing_card_impression`
- 문의 완료율 = `message_sent` / `message_compose_opened`
- 등록 완료율 = `listing_created` / `listing_create_started`

가격 구간별 차이는 개선 가설을 만드는 참고값이며 가격만이 행동의 원인이라고 단정하지 않는다.

## 3. 배포 설정

1. Google Analytics에서 GA4 속성과 웹 데이터 스트림을 만든다.
2. `G-`로 시작하는 측정 ID를 확인한다.
3. 프론트 배포 빌드 환경에 `SETTY_GA_MEASUREMENT_ID`를 설정한다.
4. 기존 프론트 검증 명령을 통과한 빌드를 배포한다.

```bash
SETTY_GA_MEASUREMENT_ID=G-XXXXXXXXXX npm run build
```

측정 ID는 태그가 동작하기 위해 브라우저에 공개되는 식별자이지만, 저장소에는 환경별 실제 값을 고정하지 않는다.

## 4. 관찰과 완료 기준

아래 항목을 실제 배포 환경에서 확인하기 전에는 사용자 행동 모니터링을 완료했다고 기록하지 않는다.

- GA4 실시간 보고서에서 본인 테스트 방문 1건 확인
- `/`, `/listings/{id}`, `/listings/{id}/message` 페이지 조회 확인
- 카드가 바뀔 때 `listing_card_impression` 확인
- 넘기기 버튼과 왼쪽 스와이프의 `listing_skipped` 확인
- 카드 탭·상세 버튼·오른쪽 스와이프의 `listing_detail_opened` 확인
- 쪽지 작성 화면에서 `message_compose_opened` 확인
- 쪽지 API 성공 후 `message_sent` 확인
- 신규 등록 폼 첫 변경 후 `listing_create_started` 확인
- 매물 등록 API 성공 후 `listing_created` 확인
- 매물 이벤트의 `price_bucket`과 행동 방식 확인
- 이벤트에 전화번호·비밀번호·쪽지 내용 등 개인정보가 없는지 확인
- 실제 사용자 관찰 기간과 표본 수 기록

최소 모니터링 항목은 `사용자`, `신규 사용자`, `세션`, `유입 경로`, `기기`, `페이지 조회`와 위 행동 이벤트로 구성한다. 구매자 흐름은 `listing_card_impression → listing_detail_opened → message_compose_opened → message_sent`, 판매자 흐름은 `listing_create_started → listing_created`로 본다.

## 5. 과제 제출 기록 초안

### 수행 과정과 의사결정

기존에는 서비스 방문자 수와 사용 경로를 확인할 수 없어 개선 논의가 팀의 가설에 의존했다. 방문 현황과 유입 경로를 먼저 파악하기 위해 GA4를 선택했다. 이후 상세 조회와 문의 수만으로는 행동 비율을 계산할 수 없다는 한계를 발견해 카드 노출·넘김·문의 작성·등록 시작과 성공 이벤트를 추가했다. 가격은 원문 대신 구간으로 기록해 가격대별 반응을 비교할 수 있게 했다. 클릭만으로 성공을 판단하지 않기 위해 `message_sent`와 `listing_created`는 서버 요청이 성공한 뒤에만 기록했다.

### 관찰 결과

아래 값은 실제 배포 후 GA4에서 확인한 수치로 교체한다.

- 관찰 기간: `[시작 시각] ~ [종료 시각]`
- 사용자 수 / 신규 사용자 수: `[ ] / [ ]`
- 주요 유입 경로: `[ ]`
- 가장 많이 본 페이지: `[ ]`
- `listing_card_impression` / `listing_skipped`: `[ ]회 / [ ]회`
- `listing_detail_opened`: `[ ]회`
- `message_compose_opened` / `message_sent`: `[ ]회 / [ ]회`
- `listing_create_started` / `listing_created`: `[ ]회 / [ ]회`
- 관찰에서 발견한 점: `[실제 데이터에 근거해 작성]`
- 다음 개선 가설: `[관찰 결과와 구분해 작성]`

### 배운 점

도구를 연결하는 것만으로는 개선 근거가 생기지 않는다. 알고 싶은 질문을 먼저 정하고, 단순 클릭과 실제 기능 성공을 구분해 이벤트를 설계해야 의미 있는 행동 흐름을 확인할 수 있다. 또한 적은 표본에서 나온 결과는 결론이 아니라 다음 검증을 위한 가설로 다뤄야 한다.
