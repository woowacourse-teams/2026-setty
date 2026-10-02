<div align="center">
  <img src="client/public/setty-favicon.svg" alt="SETTY 로고" width="76" />
  <h1>SETTY</h1>
  <p><strong>중고 가구를 고르는 순간, 배송까지 생각합니다.</strong></p>
  <p>매물 탐색 · 배송비 확인 · 결제를 하나의 거래 흐름으로</p>
</div>

---

## 🪑 가구 거래가 멈추는 지점

당근의 [2025 연말결산](https://about.daangn.com/company/pr/archive/%EB%8B%B9%EA%B7%BC-2025-%EC%97%B0%EB%A7%90%EA%B2%B0%EC%82%B0-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EA%B3%B5%EA%B0%9C/)에서 **가구·인테리어는 나눔이 가장 많았던 물품 카테고리**였습니다. 이 자료만으로 나눔의 원인을 단정할 수는 없습니다. SETTY는 가구 거래에서 가격만큼이나 운송을 결정하기 어렵다는 문제에 주목합니다.

가구는 부피와 무게 때문에 구매 후 운송 계획이 필요합니다. 차량이 없는 구매자는 마음에 드는 매물을 발견해도 판매자와 일정을 맞추고, 운송 견적을 받고, 운송을 별도로 신청해야 합니다. 저렴한 매물을 찾은 뒤에도 **최종 비용과 배송 가능 여부를 바로 알기 어렵습니다.**

| 기존 · 플랫폼을 오가는 거래 | SETTY · 한 흐름에서 진행 |
| :---: | :---: |
| **중고거래 플랫폼** · 매물 탐색<br>↓<br>**판매자** · 거래 조율<br>↓<br>**용달 서비스** · 운송 견적<br>↓<br>**판매자** · 일정 재조율<br>↓<br>**중고거래 플랫폼** · 물품 대금 송금<br>↓<br>**용달 서비스** · 운송 신청·결제<br>↓<br>배송 | 매물 탐색<br>↓<br>예상 배송비·총액 확인<br>↓<br>가구·배송비 함께 결제<br>↓<br>배송 요청<br>↓<br>배송 |

SETTY가 줄이려는 것은 구매자가 판매자와 운송 서비스 사이를 오가며 직접 조율해야 하는 단계입니다.

## 🛒 한 흐름에서 끝내는 구매

1. **발견** — 중고 가구 매물을 둘러보고 상세 정보를 확인합니다.
2. **확인** — 상세 화면에서 매물 가격, 예상 배송비, 총 결제 금액을 함께 봅니다.
3. **결제** — 가구 비용과 배송비를 한 번에 결제합니다.
4. **배송** — 결제 확정 후 배송 요청이 생성되고, 기사가 요청을 수락해 배송을 진행합니다.

결제 단계의 길이는 이 흐름을 설계한 배경 중 하나입니다. Stripe의 **2022년 일반 전자상거래 결제 조사**에서는 아시아·태평양 응답자의 49%가 결제에 3분 이상 걸리면 구매를 포기한다고 답했습니다. 북미는 2분에 52%, 유럽은 2분에 62%였습니다. [아시아·태평양](https://stripe.com/guides/state-of-asia-pacific-checkouts-2022) · [북미](https://stripe.com/guides/state-of-north-american-checkouts-2022) · [유럽](https://stripe.com/guides/state-of-european-checkouts-2022)

> [!NOTE]
> **현재 구현 범위**: 예상 배송비는 주소 기반 용달 견적이 아니라 **가구의 크기(부피)에 따른 정액 정책**으로 계산합니다. 결제 후 배송 요청과 기사 수락 흐름은 구현되어 있습니다. 주소 기반 견적과 자동 차량 매칭은 아직 구현되지 않았습니다.

## 🗂️ 저장소 둘러보기

| 경로 | 역할 | 기술 |
| :-- | :-- | :-- |
| [`client/`](client/) | 구매자·판매자 웹, 매물 탐색과 결제 | React · TypeScript · Webpack |
| [`android/`](android/) | Android 앱, 매물 탐색과 판매자 화면 | Kotlin · Jetpack Compose |
| [`apps/driver/`](apps/driver/) | 기사 앱, 요청 수락부터 배송 완료까지 | Expo · React Native · TypeScript |
| [`server/`](server/) | 회원·매물·주문·결제·배송 API | Java 21 · Spring Boot · MySQL |
| [`docs/policy/`](docs/policy/README.md) | 주문·결제·배송의 상태와 서비스 정책 | Markdown |

### 코드에서 확인하는 핵심 흐름

- [배송비 정책](server/src/main/java/setty/platform/listing/domain/DeliveryFeePolicy.java): 가구 부피에 따라 예상 배송비를 계산합니다.
- [매물 상세](client/src/components/ProductDetail.tsx): 매물 가격, 배송비, 총액을 함께 표시합니다.
- [결제 화면](client/src/components/PaymentCheckout.tsx): 주문을 생성하고 결제 위젯을 엽니다. 목 모드에서는 결제 성공을 모의합니다.
- [배송 정책](docs/policy/delivery.md): 결제 확정 이후 배송 요청과 상태 전이를 정의합니다.

## 📚 문서와 협업

- [서비스 정책](docs/policy/README.md): 주문·결제·배송의 확정 규칙과 미정 항목
- [기사 앱 문서](apps/docs/README.md): 앱 구조, API 매핑, 확인할 사항
- [CI](.github/workflows/ci.yml): 서버 테스트와 웹 타입 검사
