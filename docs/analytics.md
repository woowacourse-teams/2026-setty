# 사용자 행동 수집

구매자 웹은 PostHog로 상품 조회와 결제 흐름을 수집한다. 수집 코드는 [`client/src/analytics/posthog.ts`](../client/src/analytics/posthog.ts)에서 관리한다.

## 연결 설정

유지: 프로젝트 키와 API Host는 빌드 환경변수로 주입한다. 소스에 키를 직접 넣지 않는다. 이미 수집 중인 배포 환경은 기존 값을 유지한다.

| 환경변수 | 용도 | 기본값 |
| --- | --- | --- |
| `SETTY_POSTHOG_KEY` | PostHog 프로젝트 API 키(`phc_…`) | 없음. 유효한 키가 없으면 수집하지 않는다. |
| `SETTY_POSTHOG_HOST` | 프로젝트의 수집 API Host | `https://us.i.posthog.com` |

EU 프로젝트는 `https://eu.i.posthog.com`을 사용한다. 개인 API 키가 아니라 프로젝트 API 키를 사용한다.

로컬에서 수집을 확인할 때는 테스트 프로젝트 값을 넣고 개발 서버를 실행한다.

```sh
cd client
SETTY_POSTHOG_KEY='phc_PROJECTKEY' \
SETTY_POSTHOG_HOST='https://us.i.posthog.com' \
npm run dev:msw
```

`phc_PROJECTKEY`는 실제 테스트 프로젝트 키로 교체한다. Webpack이 빌드 시 값을 읽으므로 환경변수 변경 후 개발 서버를 다시 시작하거나 재빌드해야 한다. `.env` 파일은 자동으로 읽지 않는다.

배포는 CodeBuild의 기존 환경변수를 사용한다. [`client/buildspec.yml`](../client/buildspec.yml)은 `SETTY_*` 값을 빌드 변경 감지에 포함한다.

## 이벤트 정의

| 이벤트 | 발생 시점 | 속성 |
| --- | --- | --- |
| `product_viewed` | 상품 상세 조회가 성공하고 해당 상품 화면이 표시됨 | `listing_id` |
| `checkout_opened` | SETTY 결제 모달을 엶 | `listing_id`, `amount` |
| `checkout_closed` | SETTY 결제 모달을 닫음 | `listing_id` |
| `payment_succeeded` | 서버 결제 처리 후 성공 결과로 웹에 복귀함 | `order_id` |
| `payment_failed` | 서버 실패 결과로 웹에 복귀하거나, 주문 생성 후 토스 결제 요청이 취소·실패함 | `order_id`, `code` |

`listing_id`는 숫자이며, `order_id`는 내부 주문 ID의 문자열 표현이다. 토스 주문 ID(`<내부 주문 ID>_<랜덤>`)에서는 앞부분을 사용한다. 복귀 URL에 주문 ID가 없으면 해당 속성은 전송하지 않는다. 실패 사유는 서버의 `reason` 또는 토스 복귀의 `code`에서 읽는다.

모든 수집 이벤트에는 공통 속성 `is_mock`이 포함된다. `true`는 프런트엔드 MSW 모드이며, 이 모드의 `payment_succeeded`는 서버 결제 승인·저장을 뜻하지 않는다. 운영 집계에서는 `is_mock = false`로 필터링한다.

유지: SPA 경로 변경의 `$pageview`, 자동 클릭 수집, 로그인 성공 시 로그인 아이디로 `identify`, 로그아웃 시 `reset`을 사용한다. 브라우저 SDK의 식별 정보는 새로고침과 결제 복귀에도 유지된다. 리플레이의 입력값은 마스킹하며, 비밀번호·연락처·주소는 커스텀 이벤트 속성에 넣지 않는다.

## 지표 기준

채택: **WAU는 한 주 동안 `product_viewed`를 발생시킨 고유 사용자 수**다. 집계 범위는 `Asia/Seoul` 기준 월요일 00:00 이상부터 다음 월요일 00:00 미만이다. 일별 고유 사용자 수를 더하지 않고 주간 전체에서 사용자를 중복 제거한다. 익명 사용자는 브라우저의 식별자로, 로그인 사용자는 PostHog가 연결한 사용자로 집계한다.

- 최종 결제 전환율: 해당 주에 상품을 조회한 사용자 중 같은 주에 결제를 완료한 사용자 수 / WAU × 100
- 결제 모달 진입 대비 전환율: 해당 주에 결제 모달에 진입한 사용자 중 같은 주에 결제를 완료한 사용자 수 / 결제 모달 진입 고유 사용자 수 × 100

PostHog Trends에서 `product_viewed`의 `Unique users`를 주간 범위로 조회한다. 퍼널은 `product_viewed` → `checkout_opened` → `payment_succeeded` 순서로 설정하고, 전환 기간과 집계 주의 경계를 명시한다. 집계 정의는 [PostHog Trends](https://posthog.com/docs/product-analytics/trends/overview)와 [Funnels](https://posthog.com/docs/product-analytics/funnels)를 참고한다.

`payment_succeeded`는 성공 화면 복귀 이벤트다. 실제 결제 완료 수는 `order_id`로 서버의 `payments.status = 'DONE'` 기록과 대조한다. 승인·취소·주문 만료 정책은 [결제 정책](policy/payment.md)과 [주문·매물 정책](policy/order.md)을 따른다.

## 수집 확인

1. 상품 상세를 정상 조회하면 `product_viewed`에 해당 `listing_id`가 포함되는지 확인한다. 조회 실패는 포함하지 않는다.
2. 같은 화면의 이미지 변경·찜 처리·모달 열기·닫기가 추가 상품 조회로 기록되지 않는지 확인한다. 상품 재방문은 새 조회로 기록하되 고유 사용자 수는 유지한다.
3. 결제 모달 열기·닫기를 확인한다. 모달 열기 자체는 주문 생성이 아니다.
4. 성공 복귀의 `order_id`, 실패 복귀의 `order_id`·`code`, 데스크톱 토스 결제 취소 이벤트를 확인한다.
5. 같은 브라우저에서 로그인·새로고침·결제 복귀 시 사용자 식별이 유지되고 로그아웃 후 초기화되는지 확인한다.
6. MSW 모드에서는 `is_mock = true`인지 확인한다. 실제 서버 결제 검증은 테스트 승인과 서버 기록 대조로 수행한다.

## 테스트 계정 준비

[`scripts/create_k6_test_users.sh`](../scripts/create_k6_test_users.sh)는 기본 100개의 구매자 계정을 생성하거나, 같은 아이디가 이미 있으면 로그인을 확인한다. 기본 API 주소는 `https://www.setty.cloud/dev`이며 다른 격리된 개발·스테이징 환경은 `BASE_URL`로 지정한다. 회원가입 비밀번호는 서버 규칙상 8~64자여야 한다.

```sh
scripts/create_k6_test_users.sh
```

실행하면 테스트 비밀번호를 터미널에서 입력한다. 자동화할 때는 `PASSWORD` 환경변수로 전달한다. 예시 비밀번호는 서버의 최소 길이 규칙에 맞게 8자 이상이어야 한다.

계정 수와 접두사는 `COUNT`, `ACCOUNT_PREFIX`로, CSV 저장 위치는 `CSV_PATH`로 바꿀 수 있다. 기본 파일은 저장소의 `.local/k6-test-users.csv`이며 Git에서 제외된다. CSV에는 평문 비밀번호가 들어가므로 파일 권한은 소유자만 읽고 쓸 수 있게 설정되며, 저장소에 추가하지 않는다. 계정은 서버 DB에 남고 앱에 회원 삭제 API가 없으므로 격리된 개발·스테이징 DB에서만 실행한다. 같은 접두사로 재실행하려면 기존 계정의 비밀번호도 지정한 값과 같아야 한다.

이 스크립트는 인증 계정만 준비하며 매물, 로그인 세션, PostHog 이벤트는 만들지 않는다. 결제 시나리오에는 별도 판매자 계정과 구매 가능한 매물이 필요하다. 성공 결제 20건을 만들려면 각 상품이 주문에 선점되고 결제 완료 후 판매 처리되므로 최소 20개의 서로 다른 판매 가능 매물을 준비한다. 각 가상 사용자는 서로 다른 계정과 브라우저 세션을 써야 한다. 일반 k6 HTTP 요청만으로는 프런트엔드의 `product_viewed` 같은 이벤트가 발생하지 않으므로, 지표 검증은 브라우저 시나리오로 수행하거나 별도 합성 이벤트로 구분한다. 테스트 이벤트는 전용 PostHog 프로젝트에 보내는 것을 권장한다.
