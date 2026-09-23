# 아키텍처와 폴더 규칙

유지보수를 위해 화면·컴포넌트·스타일·데이터 계층을 분리하고, 한 파일이 과도하게 길지 않게 유지한다.

## 폴더 구조

```
apps/driver/
├─ app/                         # Expo Router 라우트(파일 = 화면). 얇게 유지.
│  ├─ _layout.tsx               # 루트 Stack + 폰트 로딩 + AuthProvider + 인증 게이트(Stack.Protected)
│  ├─ login.tsx                 # 로그인(게스트 전용)
│  ├─ signup.tsx                # 회원가입(게스트 전용)
│  ├─ (tabs)/                   # 하단 탭 그룹(인증 필요)
│  │  ├─ _layout.tsx            #   Tabs + 커스텀 BottomTabBar
│  │  ├─ index.tsx              #   요청(홈)
│  │  ├─ shipments.tsx          #   내 배차
│  │  └─ settlement.tsx         #   정산(+ 로그아웃)
│  ├─ request/[deliveryId].tsx  # 수신 상세(탭 위에 push)
│  └─ shipment/[deliveryId].tsx # 내 배차 상세(수령·완료)
├─ src/
│  ├─ components/               # 화면 공용 UI (AppText, Screen, TextField, StatusPill, RouteLine, PrimaryButton, Toast, BottomTabBar)
│  ├─ features/                 # 도메인 화면 + 화면 전용 훅/스타일
│  │  ├─ auth/                  #   AuthContext · 로그인/회원가입 화면 · validators
│  │  ├─ requests/              #   요청·수신·수락 슬라이스
│  │  ├─ shipments/             #   내 배차 목록·상세
│  │  └─ settlement/            #   정산(완료 배차에서 앱이 계산)
│  ├─ theme/                    # 디자인 토큰: colors, spacing, radius, typography, shadows
│  ├─ model/                    # 서버 계약 타입(delivery.ts, auth.ts)
│  ├─ lib/                      # config, http, tokenStore, errorMessage, format, statusMeta
│  └─ api/                      # deliveryApi, deliveryAuthApi + mock/
└─ assets/                      # (폰트는 @expo-google-fonts로 코드 로딩)
```

## 규칙

- **라우트(`app/`)는 얇게.** 실제 화면 구현은 `src/features/<도메인>`에 두고 라우트는 파라미터만 넘겨 렌더한다.
- **스타일 분리.** 화면·큰 컴포넌트는 옆에 `*.styles.ts`를 둔다. 색·여백·폰트 등 값은 반드시 `src/theme` 토큰을 참조하고, 컴포넌트 스타일에는 레이아웃과 크기만 둔다.
- **폰트는 `AppText`로만.** `fontFamily`를 직접 쓰지 않고 `AppText`의 `variant`(display/regular/medium/bold/black)로 지정한다. 디스플레이(제목·금액)는 Do Hyeon, 본문은 Noto Sans KR.
- **데이터 계층 단방향.** 화면 → 훅(`useXxx`) → `api/deliveryApi` → (`http` | `mock`). 화면에서 `fetch`를 직접 호출하지 않는다.
- **타입은 `model`이 출처.** 서버 계약 필드명을 그대로 유지하고, 계약에 없는 필드를 추측해 추가하지 않는다.
- **아이콘은 인라인 벡터/텍스트.** 이모지·딩벳을 UI 요소로 쓰지 않는다.

## 인증 흐름

- **상태 출처: `features/auth/AuthContext`.** `loading | authed | guest` 3상태. 앱 시작 시 `tokenStore.load()`로 SecureStore의 토큰을 읽어 authed/guest를 정한다.
- **게이트: `app/_layout.tsx`의 `Stack.Protected`.** authed면 탭·상세만, guest면 로그인·회원가입만 접근 가능하며 전환 시 자동 리다이렉트한다.
- **토큰 저장: `lib/tokenStore.ts`.** SecureStore(영속) + 메모리 캐시(동기 조회). `http`는 요청마다 메모리 토큰을 `Authorization: Bearer`로 붙인다.
- **만료 처리: `lib/http.ts` → `setOnUnauthorized`.** 응답이 `401 INVALID_TOKEN`이면 등록된 훅(AuthProvider)이 토큰을 지우고 guest로 떨어뜨려 로그인 화면으로 보낸다.
- **에러 문구: `lib/errorMessage.ts`.** 서버 `{ code, message }`를 사용자 문구로 매핑(코드표는 api-mapping.md).
- **목 모드:** 베이스 URL이 비면 인증도 목(`api/mock/authMock.ts`)으로 처리해 서버 없이 로그인 흐름을 확인할 수 있다.

## 디자인 대응 메모

- 원본 디자인은 웹(HTML/CSS) 프로토타입이다. 가짜 상태바·가짜 키보드는 그리지 않고 실제 OS 것을 쓴다(SafeArea).
- 웹 전용 효과(`backdrop-filter`, 그라디언트 배경, pointer 스와이프)는 RN 등가물로 옮기거나 후속으로 미룬다. 홈 카드의 스와이프-거절은 제스처 라이브러리가 필요해 이번엔 제외하고, 거절은 수신 상세의 버튼으로 제공한다(아래 open-questions 참고).
