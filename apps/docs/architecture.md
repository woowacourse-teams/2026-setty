# 아키텍처와 폴더 규칙

유지보수를 위해 화면·컴포넌트·스타일·데이터 계층을 분리하고, 한 파일이 과도하게 길지 않게 유지한다.

## 폴더 구조

```
apps/driver/
├─ app/                         # Expo Router 라우트(파일 = 화면). 얇게 유지.
│  ├─ _layout.tsx               # 루트 Stack + 폰트 로딩 + SafeAreaProvider
│  ├─ index.tsx                 # 홈(요청 목록)
│  └─ request/[deliveryId].tsx  # 수신 상세
├─ src/
│  ├─ components/               # 화면 공용 UI (AppText, Screen, StatusPill, RouteLine, PrimaryButton, Toast)
│  ├─ features/                 # 도메인 화면 + 화면 전용 훅/스타일
│  │  └─ requests/              #   요청·수신·수락 슬라이스
│  ├─ theme/                    # 디자인 토큰: colors, spacing, radius, typography, shadows
│  ├─ model/                    # 서버 계약 타입(delivery.ts)
│  ├─ lib/                      # config, http, format, statusMeta
│  └─ api/                      # deliveryApi + mock/
└─ assets/                      # (폰트는 @expo-google-fonts로 코드 로딩)
```

## 규칙

- **라우트(`app/`)는 얇게.** 실제 화면 구현은 `src/features/<도메인>`에 두고 라우트는 파라미터만 넘겨 렌더한다.
- **스타일 분리.** 화면·큰 컴포넌트는 옆에 `*.styles.ts`를 둔다. 색·여백·폰트 등 값은 반드시 `src/theme` 토큰을 참조하고, 컴포넌트 스타일에는 레이아웃과 크기만 둔다.
- **폰트는 `AppText`로만.** `fontFamily`를 직접 쓰지 않고 `AppText`의 `variant`(display/regular/medium/bold/black)로 지정한다. 디스플레이(제목·금액)는 Do Hyeon, 본문은 Noto Sans KR.
- **데이터 계층 단방향.** 화면 → 훅(`useXxx`) → `api/deliveryApi` → (`http` | `mock`). 화면에서 `fetch`를 직접 호출하지 않는다.
- **타입은 `model`이 출처.** 서버 계약 필드명을 그대로 유지하고, 계약에 없는 필드를 추측해 추가하지 않는다.
- **아이콘은 인라인 벡터/텍스트.** 이모지·딩벳을 UI 요소로 쓰지 않는다.

## 디자인 대응 메모

- 원본 디자인은 웹(HTML/CSS) 프로토타입이다. 가짜 상태바·가짜 키보드는 그리지 않고 실제 OS 것을 쓴다(SafeArea).
- 웹 전용 효과(`backdrop-filter`, 그라디언트 배경, pointer 스와이프)는 RN 등가물로 옮기거나 후속으로 미룬다. 홈 카드의 스와이프-거절은 제스처 라이브러리가 필요해 이번엔 제외하고, 거절은 수신 상세의 버튼으로 제공한다(아래 open-questions 참고).
