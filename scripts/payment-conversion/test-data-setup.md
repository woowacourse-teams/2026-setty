# 테스트 계정·매물 준비와 결제 전환 시나리오 실행

파일별 역할과 시나리오 지표는 [README](README.md)를 참조한다. 이 문서는 준비·실행·진단 절차의 기준이다.

부하·시나리오 테스트에 사용할 계정과 매물을 준비하는 방법이다. 명령은 저장소 루트에서 실행한다. 두 스크립트의 기본 API 주소는 `https://www.setty.cloud/dev`이며, 다른 개발·스테이징 서버는 `BASE_URL`로 지정한다.

테스트 계정은 서버 DB에, 매물 이미지 객체는 S3에 남는다. 격리된 개발·스테이징 환경에서 실행하고 운영 데이터와 섞지 않는다. dev 서버에서 매물을 만들려면 **dev 서버**에 다음 S3 설정이 적용되어 있어야 한다. prod 서버의 환경변수 변경은 dev 서버에 적용되지 않는다.

```sh
AWS_REGION=ap-northeast-2
SETTY_S3_BUCKET=techcourse-project-2026
SETTY_S3_PUBLIC_BASE_URL=https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com
```

## 구매자 계정

[`scripts/payment-conversion/create_k6_test_users.sh`](create_k6_test_users.sh)는 기본 100개의 구매자 계정을 만든다. 같은 계정이 이미 있으면 로그인으로 확인한다.

```sh
scripts/payment-conversion/create_k6_test_users.sh
```

실행 중 테스트 비밀번호를 입력한다. 기존 계정이면 등록 때 사용한 비밀번호를 입력해야 한다. 부하 시나리오에서 기본값을 사용하지 않을 때는 환경변수로 계정 수, 아이디 접두사, 결과 파일을 지정한다.

```sh
BASE_URL=https://www.setty.cloud/dev COUNT=100 ACCOUNT_PREFIX=k6user CSV_PATH=.local/k6-test-users.csv scripts/payment-conversion/create_k6_test_users.sh
```

계정 CSV는 `.local/k6-test-users.csv`에 저장되며 Git에서 제외되고 파일 권한은 소유자만 읽고 쓸 수 있게 설정된다. CSV에는 평문 비밀번호가 있으므로 저장소에 추가하지 않는다.

## 판매자 계정과 매물

매물 등록에는 구매자 계정과 분리된 판매자 계정이 필요하다. 다음 명령은 판매자 `k6seller1`을 만들거나 기존 계정을 로그인으로 확인한 뒤, 기본 20개의 판매 가능 매물을 등록한다.

```sh
BASE_URL=https://www.setty.cloud/dev COUNT=1 ACCOUNT_PREFIX=k6seller CSV_PATH=.local/k6-test-seller.csv scripts/payment-conversion/create_k6_test_users.sh
BASE_URL=https://www.setty.cloud/dev SELLER_CSV_PATH=.local/k6-test-seller.csv scripts/payment-conversion/create_k6_test_listings.sh
```

서비스 재시작만으로 DB 계정은 삭제되지 않는다. 판매자 CSV와 계정이 이미 준비되어 있으면 첫 명령은 생략하고 매물 등록 명령만 실행한다. 계정이 DB에서 삭제된 경우 첫 명령으로 다시 만들 수 있다. 기존 계정이면 이전 비밀번호를 사용해야 로그인 확인에 성공한다.

매물 등록 스크립트는 기본 이미지 [`floor-lamp.png`](../../client/public/images/listings/floor-lamp.png)를 매물마다 업로드한다. 이미지의 S3 object key prefix는 `setty/images/listings/`이고, 설정된 버킷이 `techcourse-project-2026`이면 객체는 `s3://techcourse-project-2026/setty/images/listings/` 아래에 저장된다.

결과는 `.local/k6-test-listings-<RUN_TAG>.csv`에 기록되며 `listingId,title,category,price` 열을 제공한다. `COUNT`, `RUN_TAG`, `SELLER_CSV_PATH`, `IMAGE_PATH`, `LISTINGS_CSV_PATH`, `BASE_URL`로 등록 개수와 경로를 바꿀 수 있다. 같은 `RUN_TAG`로 다시 실행하면 판매 가능한 기존 매물을 재사용해 CSV를 복구한다. 예약되거나 판매된 매물은 재사용하지 않는다. 새 `RUN_TAG`는 새 매물과 S3 이미지 객체를 만든다.

결제 완료 시 매물은 주문에 선점되고 판매 처리되므로 성공 결제 20건을 시나리오로 만들려면 서로 다른 판매 가능 매물이 최소 20개 필요하다. 생성된 테스트 데이터는 서버 DB와 S3에 남으므로 테스트 후 정리한다.

## 준비한 데이터로 브라우저 시나리오 실행

[`scripts/payment-conversion/scenario.js`](scenario.js)는 공유 프론트엔드에서 브라우저로 로그인하고, `/api/` 요청을 지정한 dev 백엔드로 연결한다. `APP_URL`에는 공유 프론트엔드 주소를, `API_BASE_URL`에는 `/dev`가 포함된 백엔드 주소를 지정한다. PostHog 요청은 프론트엔드에 설정된 수집 주소로 전송된다.

결제 복귀는 [`scripts/payment-conversion/payment.js`](payment.js)가 처리한다. 구매 그룹에서만 `/payments/success·fail`을 가로채고, 토스가 돌려준 실제 인증 결과와 로그인한 구매자의 토큰으로 dev의 `POST /api/payments/confirm·fail`을 호출한다. 승인 응답의 `DONE`·주문 ID·금액이 일치해야 앱의 정상 성공 복귀 화면으로 이동한다. 이후 같은 매물의 주문이 `CONFIRMED`가 되었는지 최대 30초 동안 확인한다. 토스 인증만 완료됐거나 화면에 성공 문구가 있다는 이유로 결제 성공을 집계하지 않는다. 복귀 결과를 확인하면 가로채기를 해제한다.

2026-10-11 확인한 공유 프론트엔드의 `/dev/payments/success`는 백엔드 승인 대신 SPA HTML을 HTTP 200으로 반환했다. 이전 방식은 복귀 URL만 이 경로로 바꾸어 승인 없이 메인 화면으로 돌아갈 수 있었다. 현재 스크립트는 이 경로에 의존하지 않으며, 서버 결제 승인 API와 주문 상태를 근거로 판정한다. 승인 API의 구현은 [`PaymentController`](../../server/src/main/java/setty/payment/presentation/PaymentController.java)를 참조한다.

먼저 계정 한 명의 로그인 식별과 상품 조회가 수집되는지 확인한다. 이 모드는 주문·결제를 실행하지 않는다. `LISTINGS_CSV`는 실제 준비한 파일 이름으로 바꾼다.

```sh
APP_URL=https://www.setty.cloud \
API_BASE_URL=https://www.setty.cloud/dev \
USERS_CSV="$PWD/.local/k6-test-users.csv" \
LISTINGS_CSV="$PWD/.local/k6-test-listings-<RUN_TAG>.csv" \
ANALYTICS_SMOKE=true \
K6_BROWSER_HEADLESS=false scripts/payment-conversion/run_scenario.sh
```

`$identify`와 `product_viewed` 각각에 `PostHog 수집 확인`이 출력되고, 확인된 이벤트가 2개이면 성공이다. PostHog의 Activity에서도 같은 `loginId`의 이벤트를 확인한다. HTTP 성공은 수집 서버의 접수 확인이며, 대시보드 반영은 별도로 새로고침해 확인한다.

전체 시나리오는 전용 실행 스크립트로 실행한다. 상품 조회와 결제창 이탈 80명이 먼저 실행되고, 두 시나리오가 모두 성공적으로 끝난 뒤에만 구매 20명이 시작된다. 각 단계의 k6 프로세스는 서로 겹치지 않는다. 앞 단계가 실패하면 구매 단계는 시작하지 않는다. `run_scenario.sh`는 필요한 환경변수만 k6 스크립트에 전달하고, 초 단위 진행률 표시를 끈 채 단계별 결과·오류는 출력한다.

```sh
APP_URL=https://www.setty.cloud \
API_BASE_URL=https://www.setty.cloud/dev \
USERS_CSV="$PWD/.local/k6-test-users.csv" \
LISTINGS_CSV="$PWD/.local/k6-test-listings-<RUN_TAG>.csv" \
K6_BROWSER_HEADLESS=false scripts/payment-conversion/run_scenario.sh
```

전체 시나리오는 상품만 확인하는 50명, 결제창에서 이탈하는 30명(재확인 10명 포함), 결제하는 20명을 실행한다. 화면에 표시된 선행 단계 결과와 구매 단계 결과 다음에 전체 합산 결과가 출력된다. 토스 테스트 결제 인증은 직접 완료해야 한다. 이미 판매된 매물이 있으면 새 매물 20개를 준비하고 새 CSV를 지정한다.

결제창 이탈은 [`scripts/payment-conversion/checkout.js`](checkout.js)가 현재 DOM에서 모달 제거 여부를 확인한다. 닫기 클릭 후에도 모달이 남아 있으면 최대 3회, 전체 10초 안에서 다시 클릭한다. 제거를 확인해야 이탈 건수를 집계하며, 실제 앱의 `checkout_closed` 이벤트 HTTP 2xx 확인도 유지한다. 계속 열려 있으면 계정·매물·시도 횟수·닫기 버튼 존재 여부를 포함해 실패한다. 선행 단계의 닫힘 확인이 실패하면 이후 이벤트 검증도 건너뛰므로, 이벤트 확인 건수 감소만으로 PostHog 전송 실패를 단정하지 않는다. 선행 단계 실패로 구매가 시작되지 않았다면 준비한 매물 CSV를 그대로 지정해 다시 실행할 수 있다. PostHog와 대조할 때는 재실행한 구간만 선택한다.

전체 구매 시나리오 전에 결제 복귀와 이벤트 수집을 한 건만 확인하려면 `PURCHASE_SMOKE=true`로 실행한다. 구매자 CSV의 첫 번째 계정과 매물 CSV의 첫 번째 매물만 사용한다. 결제한 매물이 다시 선택되지 않도록 먼저 새 매물 한 개를 등록한다. 판매자 CSV가 이미 준비되어 있어야 한다.

```sh
RUN_TAG="k6smoke$(date '+%Y%m%d%H%M%S')"
BASE_URL=https://www.setty.cloud/dev \
COUNT=1 \
RUN_TAG="$RUN_TAG" \
SELLER_CSV_PATH="$PWD/.local/k6-test-seller.csv" \
LISTINGS_CSV_PATH="$PWD/.local/k6-test-listings-$RUN_TAG.csv" \
scripts/payment-conversion/create_k6_test_listings.sh
```

같은 터미널에서 이어서 실행한다. 구매자는 `k6usermilan001`이다.

```sh
APP_URL=https://www.setty.cloud \
API_BASE_URL=https://www.setty.cloud/dev \
USERS_CSV="$PWD/.local/k6-test-users-milan.csv" \
LISTINGS_CSV="$PWD/.local/k6-test-listings-$RUN_TAG.csv" \
PURCHASE_SMOKE=true \
K6_BROWSER_HEADLESS=false scripts/payment-conversion/run_scenario.sh
```

실행 중 토스 테스트 결제는 직접 완료하고, 창을 닫지 말고 k6 결과가 출력될 때까지 기다린다. 로그는 `토스 수동 인증 대기 → 토스 인증 복귀 확인 → 결제 승인 DONE → 결제 성공(DONE / CONFIRMED) → PostHog 응답 대기` 순으로 진행된다. k6가 결제 결과와 PostHog 이벤트 4개의 HTTP 2xx를 확인하면 브라우저를 자동으로 닫는다. 요약에 `테스트 판정: 성공`이 출력되면 통과다. 이 스모크는 실제 테스트 주문과 결제를 한 건 만든다.

`결제 완료`는 승인과 주문 확정을 확인한 시점에 집계한다. 이후 PostHog가 실패하면 결제 완료 건수는 유지하고 전체 테스트는 수집 검증 실패로 판정한다. 승인 실패에는 HTTP 상태와 서버 오류 코드를 출력하고, `DONE` 이후에도 주문이 `PENDING`이면 `PaymentCompleted` 이벤트 처리 확인 오류를 출력한다. `PAYMENT_TIMEOUT_MS`는 수동 토스 인증과 승인 결과의 대기 시간이며 기본 180000ms, 최소 60000ms다. 메인 화면으로 돌아온 것만으로는 결제 승인 완료를 의미하지 않는다.

`PAYMENT_CONFIRM_FAILED`가 나오면 dev 서버의 토스 승인 응답을 확인한다. 2026-10-11 실제 단건 테스트에서 주문 57의 인증 결과는 정상적으로 도착했지만 승인 API가 HTTP 400으로 이 코드를 반환했다. 같은 날 저장소의 기존 기본 승인 키는 토스 API에서 `UNAUTHORIZED_KEY`로 거절된 것을 확인하고, [토스 공식 서버 샘플](https://github.com/tosspayments/tosspayments-sample/blob/main/express-react/server.js)의 공개 문서용 승인 키로 수정했다. 기본값은 [`application.yml`](../../server/src/main/resources/application.yml)의 `setty.payment.toss.secret-key`를 단일 기준으로 사용한다. 실제 dev 서버의 `TOSS_SECRET_KEY`가 설정되어 있으면 기본값보다 우선하므로, 현재 공유 프론트엔드의 공개 테스트 클라이언트 키와 맞는 키인지 확인해야 한다. 소스 수정은 dev 서버 재배포가 필요하고, 환경변수 수정은 dev 프로세스 재시작 후 적용된다. 서버가 실제 사용하는 키와 재배포 여부는 별도 확인이 필요하다.

토스 입력 없이 복귀 처리의 회귀 테스트만 실행할 수 있다. Node.js와 k6가 필요하며, 합성 토큰과 loopback 서버만 사용한다. 공유 프론트엔드와 실제 토스·dev 서버에는 요청하지 않는다. 성공, 승인 거절, 취소 시 주문 ID 생략, HTML 200, 다른 주문의 DONE, 주문 확정 지연·미확정, 주문 만료를 검증한다.

```sh
node scripts/payment-conversion/tests/payment_callback.test.mjs
```

결제창 닫기 회귀 테스트도 loopback 서버에서 실행할 수 있다. 정상 닫기, 첫 클릭 미처리, 닫힘 지연, animation frame 중단, 끝내 닫히지 않는 모달을 검증한다. 실제 DOM 제거와 앱 이벤트를 확인하며, 닫기 실패를 성공으로 처리하지 않는지도 검증한다.

```sh
node scripts/payment-conversion/tests/checkout_close.test.mjs
```

PostHog는 `navigator.webdriver` 또는 `HeadlessChrome`으로 인식한 브라우저의 이벤트를 차단한다. 스크립트는 **k6의 공유 프론트엔드 탭에서만** 자동화 표시를 조정하며, 실제 사용자의 수집 설정은 변경하지 않는다. 따라서 `K6_BROWSER_HEADLESS=false`를 유지한다. 근거는 [PostHog의 봇 필터 설명](https://posthog.com/docs/product-analytics/troubleshooting)과 사용 중인 SDK의 `isLikelyBot`·`capture` 구현이다.

스크립트는 앱이 보낸 실제 이벤트의 `distinct_id`, 매물 ID, HTTP 2xx 응답을 확인한 뒤 다음 페이지로 이동하거나 브라우저를 닫는다. 기본 30초 안에 확인하지 못하면 `PostHog 수집 확인 실패`로 해당 반복을 실패 처리한다. `POSTHOG_TIMEOUT_MS=60000`처럼 응답 대기 시간을 조정할 수 있다(최소 10000ms). `응답 대기`는 전송 요청을 관측했지만 HTTP 응답을 아직 확인하지 못한 상태이며, PostHog의 거절 응답을 뜻하지 않는다. 확인은 응답이 도착할 때까지 1초 간격으로 조회한다. 결과의 행동 집계와 `PostHog HTTP 2xx로 확인한 이벤트`를 함께 확인한다.

느려진 원인은 코드에서 두 가지 확인됐다. 첫째, 로그인 식별과 이벤트가 실제로 수집됐는지 응답까지 기다리는 검증이 추가됐다. 현재 잠금된 `posthog-js` 1.435.6은 기본 이벤트 배치 간격이 3초이고 초기화 코드에서 이를 바꾸지 않으므로, 브라우저 세션이 그만큼 더 열려 있을 수 있다. 둘째, 기존 k6 설정은 상품 조회·결제창 이탈·구매를 각각 2 VU로 실행하고 구매만 시작 후 3분으로 고정해, 선행 그룹이 늦으면 최대 6개 브라우저가 겹쳤다. 새 실행 스크립트는 선행 시나리오 중 최대 4 VU, 구매 중 최대 2 VU만 작동하게 순서를 나눈다. 수집 응답 확인도 고정 4초 대기 대신 1초 간격으로 확인한다. 실제 CPU·온도 값은 측정하지 않았으므로 기기 발열의 직접 원인까지 계측으로 확정한 것은 아니다.

대시보드는 같은 실행 기간과 테스트 구매자 코호트로 필터링한다. WAU는 `product_viewed`의 고유 사용자 수, 두 결제 전환율은 각각 `product_viewed → payment_succeeded`, `checkout_opened → payment_succeeded`의 고유 사용자 퍼널이다. 모든 행동과 수집이 성공하면 기대값은 WAU 100명, 최종 결제 전환율 20%, 결제창 진입 대비 전환율 40%이다. 코호트 CSV에는 비밀번호와 헤더를 제외한 `loginId`만 한 줄에 하나씩 넣고, 해당 아이디의 로그인 식별이 PostHog에 수집된 뒤 가져온다. 이전 가져오기에서 아이디가 매칭되지 않아 제외됐다면 수집 후 CSV를 다시 올린다.
