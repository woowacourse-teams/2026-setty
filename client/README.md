# 배송비 사전 안내 시나리오

목록·상세 화면의 비용 정보만 A/B로 구분한다. A는 상품 가격만, B는 상품 가격·예상 배송비·총액을 표시한다. 결제 모달의 금액과 API에 전달하는 금액은 두 조건 모두 동일하다.

## 실행 설정

시나리오를 허용할 프론트엔드는 빌드 또는 개발 서버 시작 시 다음 환경변수를 지정한다.

```sh
SETTY_ENABLE_PRICE_SCENARIO=true npm --prefix client run build
```

로컬 모의 API로 화면을 확인할 때는 다음 명령을 사용한다.

```sh
SETTY_ENABLE_PRICE_SCENARIO=true npm --prefix client run dev:msw
```

PostHog 수집에는 기존 `SETTY_POSTHOG_KEY`와 `SETTY_POSTHOG_HOST` 설정이 필요하다. 키가 없으면 이벤트를 전송하지 않는다.

브라우저 콘솔에서 아래 값을 설정하고 새로고침한다. k6에서는 첫 페이지에 진입하기 전에 같은 값을 넣는다.

```js
sessionStorage.setItem('setty:price-scenario', JSON.stringify({
    price_variant: 'A',
    scenario_run_id: 's3-price-001'
}));
location.reload();
```

B조건은 `price_variant`를 `B`로 지정한다. 두 조건은 같은 실행 ID와 서로 다른 구매자 계정을 사용한다. 조건은 문서를 불러올 때 고정되므로, 실행 중 변경하려면 새로고침해야 한다. 결제 외부 페이지를 거쳐 같은 탭으로 돌아와도 같은 origin의 `sessionStorage` 설정을 다시 사용한다.

플래그가 꺼져 있거나 설정이 누락·오류인 경우 기존 B 화면을 사용하며 시나리오 속성을 붙이지 않는다. 브라우저 탭의 시나리오 설정을 제거하려면 다음을 실행한다.

```js
sessionStorage.removeItem('setty:price-scenario');
location.reload();
```

## 이벤트와 집계

| 이벤트 | 기록 시점 |
|---|---|
| `product_viewed` | 상품 상세가 표시된 뒤 |
| `checkout_opened` | 결제 모달이 렌더링된 뒤 |
| `checkout_closed` | 결제 모달을 닫을 때 |
| `payment_succeeded` | 기존 결제 성공 복귀 처리 시점 |

유효한 시나리오 설정이 있을 때 모든 커스텀 이벤트에 `price_variant`, `scenario_run_id`, `is_synthetic=true`가 추가된다. 화면과 이벤트는 같은 설정을 사용한다. 시나리오 속성은 PostHog 영구 공통 속성으로 저장하지 않는다.

`is_mock`은 기존 MSW 모의 실행 여부이고, `is_synthetic`은 이번 시나리오 데이터 표시다. 토스 테스트 결제도 시나리오 설정이 유효하면 `is_synthetic=true`로 구분한다.

PostHog에서 같은 `scenario_run_id`를 필터링하고 `price_variant`로 나눈 뒤, `product_viewed → checkout_opened`를 고유 사용자 퍼널로 계산한다. 동일 `listing_id`를 연결하며 스모크 실행 ID는 분리한다. WAU는 결제 완료 고유 구매자로 계산하고 서버 주문 기록과 대조한다.

| 지정 행동 | A | B |
|---|---:|---:|
| 상세 확인 후 종료 | 30명 | 25명 |
| 결제창 진입 후 닫기 | 15명 | 20명 |
| 결제 완료 | 5명 | 5명 |

100명 시나리오의 기대 결제창 진입률은 A 40%, B 50%이고 가상 WAU는 총 10명이다. 이 문서는 클라이언트 계측과 설정 방법을 다룬다. 계정 배정과 100명 k6 실행은 별도로 준비한다.

## 검증

```sh
npm --prefix client test
npm --prefix client run type-check
npm --prefix client run build
```
