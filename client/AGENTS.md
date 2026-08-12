# SETTY client guidance

루트 `AGENTS.md`와 함께 적용한다.

## Session start

- 세션 시작 위치와 관계없이 먼저 담당 영역을 `estimate`, `dispatch`, `operator`, `common` 중 하나로 밝히고 소유 경로를 적는다.
- 모든 client 작업은 루트와 이 파일을 읽는다. flow 작업은 해당 flow의 `AGENTS.md`도 읽는다.
- 현재 GitHub Issue의 포함·제외·완료 조건을 확인한 뒤 편집한다.
- Issue에 없는 다른 flow 또는 공동 영역 변경이 필요하면 작업을 확대하지 말고 영향과 이유를 먼저 보고한다.

## Confirmed environment

- React, TypeScript, Webpack
- 스타일: 전역 디자인 토큰과 기반 스타일 + CSS Modules
- Node.js 20 이상
- package manager: npm
- 설치: `npm ci`
- 실행: `npm run dev`
- lint: `npm run lint`
- typecheck: `npm run typecheck`
- 테스트: `npm test`
- CI 테스트: `npm run test:ci`
- 빌드: `npm run build`

## Structure and ownership

- `src/app/**`: 앱 시작, 향후 승인된 라우팅, 전역 설정을 조합하는 공동 영역
- `src/flows/estimate/**`: 견적 FE 소유 영역
- `src/flows/dispatch/**`: 배차 FE 소유 영역
- `src/flows/operator/estimate/**`: 견적 FE가 소유하는 운영자 견적 영역
- `src/flows/operator/dispatch/**`: 향후 배차 FE가 소유하는 운영자 배차 영역
- `src/flows/operator/auth/**`, `src/flows/operator/shell/**`: 두 FE 공동 운영자 영역
- `src/shared/**`: 두 flow에서 실제로 공통임이 확인된 코드와 스타일만 두는 공동 영역
- `config/**`, `eslint.config.mjs`, `package*.json`, `src/index.tsx`, Webpack과 전역 스타일: 공동 설정 영역

견적과 배차 담당자는 상대 flow를 직접 수정하거나 import하지 않는다. `app`, `shared`, operator의 auth·shell, 공동 설정과 디자인 토큰은 한 명이 변경하고 다른 FE가 리뷰한다. 아직 한 flow에서만 쓰는 코드를 예상만으로 `shared`에 올리지 않는다.

## Before editing

- `docs/product/user-operation-flow.md`, 관련 DEC와 현재 Issue를 읽는다.
- server 계약이 없으면 임의 응답 구조를 확정하지 않는다.
- 화면별로 볼 수 있는 정보와 숨길 상대방 정보를 확인한다.
- 디자인 기준은 `FRONTEND_DECISIONS.md`와 `src/shared/styles/tokens.css`를 사용한다. 제공된 디자인 초기 시안에서 확인되지 않는 값을 공통값으로 추측하지 않는다.

## Product behavior

- 예상 견적과 배차 요청은 별도 진입점과 별도 요청이다.
- 예상 견적 사용자는 문자 안내 후 정상 종료할 수 있다.
- 배차 요청은 실제 거래 정보를 다시 받는다.
- 구매자에게 판매자 상세정보를, 판매자에게 구매자 상세정보를 표시하지 않는다.
- 가격 조회, 문자, 차량 판단, 운송사 접수와 예외 대응을 자동 처리처럼 표현하지 않는다.

## Styling and shared design

- 공통 디자인 값은 `src/shared/styles/tokens.css` 한곳에서 관리한다.
- 토큰이나 전역 스타일 변경에는 다른 FE 리뷰가 필요하다.
- `global.css`에는 reset과 앱 전체 기반만 둔다. 화면 스타일은 기본적으로 `*.module.css`로 해당 소유 영역에 둔다.
- focus, error, disabled 등 시안에서 의미가 확인되지 않은 상태 토큰은 이름이나 값을 임의로 만들지 않는다.
- Pretendard는 font-family 후보만 기록한다. 별도 합의 없이 CDN이나 폰트 파일 의존성을 추가하지 않는다.

## New dependency stop condition

새 의존성이 필요해 보이면 설치하거나 lockfile을 바꾸기 전에 다음을 제안하고 팀·코치 확인 필요 여부를 밝힌다.

1. 문제와 실제 근거
2. 직접 구현을 포함한 대안
3. 선택 이유와 트레이드오프
4. 번들·운영 영향과 검증 방법
5. 재검토 조건

React Router는 Issue #12의 사용자·운영자 직접 URL, 상세 경로와 인증 이동을 위해 승인됐다. Next.js, Tailwind, shadcn/ui, TanStack Query, Zustand, React Hook Form, Zod는 선제 도입하지 않는다.

## Verification

- 정상·로딩·입력 오류·server 실패·빈 상태를 변경 범위에 맞게 확인한다.
- API 오류를 성공으로 표시하지 않는다.
- 브라우저 로그·목 데이터·스냅샷에 개인정보나 비밀을 남기지 않는다.
- `npm run lint`, `npm run typecheck`, `npm run test:ci`, `npm run build`를 실행한다.
- flow 경계 변경은 반대 flow 직접 import가 lint에서 실패하는지 임시 파일로 확인하고 임시 파일을 삭제한다.
- UI 변경은 앱 진입, 직접 URL, 새로고침, 콘솔 오류와 모바일 기본 레이아웃을 브라우저에서 확인한다.
