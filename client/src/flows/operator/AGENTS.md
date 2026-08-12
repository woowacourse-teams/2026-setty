# SETTY operator flow guidance

루트 `AGENTS.md`와 `client/AGENTS.md`를 함께 적용한다.

## Scope and ownership

- `operator`는 계정 없는 단일 운영자가 견적과 향후 배차 요청을 처리하는 독립 화면 흐름이다.
- 견적 FE는 `operator/estimate/**`를 소유한다.
- 배차 FE는 실제 배차 운영 화면을 시작할 때 `operator/dispatch/**`를 소유한다.
- `operator/auth/**`와 `operator/shell/**`은 두 FE의 공동 영역이다. 한 명이 변경하고 다른 FE가 리뷰한다.
- 현재 Issue #12에서는 `client/src/flows/dispatch/**`를 수정하거나 import하지 않는다.
- `operator`는 `estimate`나 `dispatch`를 직접 import하지 않는다. 앱 조합은 `src/app/**`에서 수행한다.

## Issue #12 behavior

- 운영자는 목록에서 이름·연락처를 보지 않고 상세 화면에서만 확인한다.
- 화면은 가격을 계산하거나 문자를 보내지 않는다.
- 운영자가 실제 문자를 보낸 뒤 내용·운송 판단·예상 금액을 저장한다.
- 저장이 성공하면 요청 상태는 운송 가능 여부와 관계없이 `ESTIMATE_NOTIFIED`가 된다.
- 완료된 기록은 수정 폼이 아니라 읽기 전용 결과로 보여준다.

## Issue #28 behavior

- 운영자는 배차 목록을 최신순으로 보고 상태로 좁힐 수 있다.
- 상세에서 구매자·판매자 정보와 판매자 입력 링크를 확인한다.
- 판매자 정보가 없으면 오류가 아니라 `판매자 입력 대기`로 표시한다.
- 최종 금액·확인 시각·운영 메모·종료 사유는 응답이 있을 때만 읽기 전용으로 표시한다.
- 배차 쓰기 API가 마련되기 전에는 상태 변경·메모 입력·문자 기록·삭제 UI를 만들지 않는다.

## Authentication boundary

- 일반 사용자 계정과 운영자 계정 시스템을 만들지 않는다.
- 운영자가 입력한 공유 비밀번호는 서버가 `SETTY_OPERATOR_SECRET` 환경 변수 값과 비교한다.
- 로그인 화면은 공통 인증 API `GET /api/operator/auth`를 `X-Operator-Secret` 헤더로 호출해 비밀번호를 검증한다.
- 검증된 비밀번호는 현재 탭의 `sessionStorage`에만 보관하고 운영자 API 요청 헤더에 사용한다.
- 실제 비밀번호를 프론트 코드, 환경 번들, 소스 저장소, 로그, 테스트에 하드코딩하지 않는다.
- 운영자 API가 `401`을 반환하면 저장된 비밀번호를 삭제하고 로그인 화면으로 이동한다.
- 로그아웃은 저장된 비밀번호를 삭제하며 서버 API를 호출하지 않는다.
- 실제 개인정보를 사용하는 공개 전에는 HTTPS, 허용 Origin·헤더, 로그인 시도 제한과 비밀번호 교체 방식을 검증한다.

## API contract stop condition

현재 프론트 구현은 `client/ESTIMATE_OPERATOR_FLOW.md`의 계약을 사용한다. server 계약이 다르면 프론트에서 추측해 우회하지 말고 Issue #12와 해당 문서를 먼저 갱신한다.

## Verification

- 로그인 성공·실패, 새로고침 유지, `401` 시 저장값 삭제와 로그아웃을 확인한다.
- 미인증 사용자가 목록·상세 URL과 API에 접근하지 못하는지 확인한다.
- 목록 응답에 이름·연락처가 없는지 확인한다.
- 정상·로딩·빈 목록·조회 실패·404·409·500 상태를 확인한다.
- 브라우저와 테스트 데이터에는 명백한 가상 개인정보만 사용한다.
