# 테스트 계정과 매물 준비

부하·시나리오 테스트에 사용할 계정과 매물을 준비하는 방법이다. 명령은 저장소 루트에서 실행한다. 두 스크립트의 기본 API 주소는 `https://www.setty.cloud/dev`이며, 다른 개발·스테이징 서버는 `BASE_URL`로 지정한다.

테스트 계정은 서버 DB에, 매물 이미지 객체는 S3에 남는다. 격리된 개발·스테이징 환경에서 실행하고 운영 데이터와 섞지 않는다. dev 서버에서 매물을 만들려면 **dev 서버**에 다음 S3 설정이 적용되어 있어야 한다. prod 서버의 환경변수 변경은 dev 서버에 적용되지 않는다.

```sh
AWS_REGION=ap-northeast-2
SETTY_S3_BUCKET=techcourse-project-2026
SETTY_S3_PUBLIC_BASE_URL=https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com
```

## 구매자 계정

[`scripts/create_k6_test_users.sh`](../scripts/create_k6_test_users.sh)는 기본 100개의 구매자 계정을 만든다. 같은 계정이 이미 있으면 로그인으로 확인한다.

```sh
scripts/create_k6_test_users.sh
```

실행 중 테스트 비밀번호를 입력한다. 기존 계정이면 등록 때 사용한 비밀번호를 입력해야 한다. 부하 시나리오에서 기본값을 사용하지 않을 때는 환경변수로 계정 수, 아이디 접두사, 결과 파일을 지정한다.

```sh
BASE_URL=https://www.setty.cloud/dev COUNT=100 ACCOUNT_PREFIX=k6user CSV_PATH=.local/k6-test-users.csv scripts/create_k6_test_users.sh
```

계정 CSV는 `.local/k6-test-users.csv`에 저장되며 Git에서 제외되고 파일 권한은 소유자만 읽고 쓸 수 있게 설정된다. CSV에는 평문 비밀번호가 있으므로 저장소에 추가하지 않는다.

## 판매자 계정과 매물

매물 등록에는 구매자 계정과 분리된 판매자 계정이 필요하다. 다음 명령은 판매자 `k6seller1`을 만들거나 기존 계정을 로그인으로 확인한 뒤, 기본 20개의 판매 가능 매물을 등록한다.

```sh
BASE_URL=https://www.setty.cloud/dev COUNT=1 ACCOUNT_PREFIX=k6seller CSV_PATH=.local/k6-test-seller.csv scripts/create_k6_test_users.sh
BASE_URL=https://www.setty.cloud/dev SELLER_CSV_PATH=.local/k6-test-seller.csv scripts/create_k6_test_listings.sh
```

서비스 재시작만으로 DB 계정은 삭제되지 않는다. 판매자 CSV와 계정이 이미 준비되어 있으면 첫 명령은 생략하고 매물 등록 명령만 실행한다. 계정이 DB에서 삭제된 경우 첫 명령으로 다시 만들 수 있다. 기존 계정이면 이전 비밀번호를 사용해야 로그인 확인에 성공한다.

매물 등록 스크립트는 기본 이미지 [`floor-lamp.png`](../client/public/images/listings/floor-lamp.png)를 매물마다 업로드한다. 이미지의 S3 object key prefix는 `setty/images/listings/`이고, 설정된 버킷이 `techcourse-project-2026`이면 객체는 `s3://techcourse-project-2026/setty/images/listings/` 아래에 저장된다.

결과는 `.local/k6-test-listings-<RUN_TAG>.csv`에 기록되며 `listingId,title,category,price` 열을 제공한다. `COUNT`, `RUN_TAG`, `SELLER_CSV_PATH`, `IMAGE_PATH`, `LISTINGS_CSV_PATH`, `BASE_URL`로 등록 개수와 경로를 바꿀 수 있다. 같은 `RUN_TAG`로 다시 실행하면 판매 가능한 기존 매물을 재사용해 CSV를 복구한다. 예약되거나 판매된 매물은 재사용하지 않는다. 새 `RUN_TAG`는 새 매물과 S3 이미지 객체를 만든다.

결제 완료 시 매물은 주문에 선점되고 판매 처리되므로 성공 결제 20건을 시나리오로 만들려면 서로 다른 판매 가능 매물이 최소 20개 필요하다. 생성된 테스트 데이터는 서버 DB와 S3에 남으므로 테스트 후 정리한다.
