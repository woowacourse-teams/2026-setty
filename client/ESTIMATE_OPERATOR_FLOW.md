# Issue #12 견적 요청·운영자 화면 구현 기준

- 기준 Issue: `#12 feat: 예상 견적 제출과 관리자 문자 안내 완료 기록 기능`
- 구현 브랜치: `feature/12-client-estimate-operator`
- 현재 단계: FE 계약·개인정보 동의 UI·공유 비밀번호 헤더 인증 구현. 견적 API·운영자 인터셉터·DEV CORS는 `develop`에 통합됐고, server 동의 증적·삭제와 실제 DEV 환경 검증은 후속 확인 필요
- 테스트 데이터: 자동화 테스트·로그·스크린샷에는 실제 개인정보가 아닌 명백한 가상 데이터만 사용

## 완성할 결과

```text
사용자 견적 제출
→ PENDING_REVIEW 저장
→ 운영자 로그인
→ 목록·상세 확인
→ 운영자가 외부에서 문자 발송
→ 보낸 문자와 운영 결과 저장
→ ESTIMATE_NOTIFIED 확인
```

시스템은 가격을 계산하거나 문자를 발송하지 않는다. 견적만 받고 종료하는 것도 정상 결과다.

## 경로와 소유권

| URL                               | 소유 경로                    | 결과                         |
| --------------------------------- | ---------------------------- | ---------------------------- |
| `/`                               | 공용 app + `flows/dispatch`  | 배차 시작·예상 견적 이동     |
| `/seller-input/:token`            | `flows/dispatch/**`          | 판매자 정보 입력             |
| `/final-amount/:token`            | `flows/dispatch/**`          | 구매자 최종 금액 확인        |
| `/estimate`                       | `flows/estimate/**`          | 사용자 견적 입력·제출        |
| `/estimate/privacy`               | `flows/estimate/**`          | 견적 개인정보 처리 안내      |
| `/estimate/submitted`             | `flows/estimate/**`          | 수동 문자 안내 예정임을 표시 |
| `/operator/login`                 | `flows/operator/auth/**`     | 서버 공유 비밀번호 입력·검증 |
| `/operator/estimate-requests`     | `flows/operator/estimate/**` | 개인정보 없는 최신순 목록    |
| `/operator/estimate-requests/:id` | `flows/operator/estimate/**` | 상세 확인·문자 완료 기록     |

`app`은 라우팅만 조합한다. estimate·operator·dispatch는 서로 직접 import하지 않는다. 최신 `develop`의 배차 화면은 공용 app 어댑터에서 루트·토큰 URL에 연결하고, `flows/dispatch/**`는 Issue #12에서 변경하지 않는다.

각 사용자 flow의 경로 정의는 해당 flow의 `routes.tsx`가 소유하고, `src/app/routes/AppRoutes.tsx`는 route 배열만 합친다. 운영자 영역은 `flows/operator/routes.tsx`가 공용 로그인·보호·shell과 하위 route 조합을 담당하고, 견적 화면 경로는 `flows/operator/estimate/routes.tsx`가 소유한다. 다음 배차 운영 이슈에서는 `flows/operator/dispatch/routes.tsx`를 새로 추가하고 공용 operator 조합 파일에 한 번 연결해 견적 화면 파일의 충돌을 피한다. 현재 `/operator` 기본 이동은 견적 목록이지만, 배차 화면을 추가할 때 공동 대시보드 또는 기본 화면을 정해야 하는 의도적인 공용 결정 지점이다.

`src/shared/api/http.ts`는 estimate와 operator가 실제로 함께 사용하는 최소 HTTP 기반이다. 배차 FE가 별도 공용 클라이언트를 중복 생성하지 않고, 이 파일을 공동 변경해야 할 때 두 FE가 먼저 계약을 확인한다.

`/`는 Issue #12가 견적 전용 진입점으로 소유하지 않는다. 배차 FE가 `develop`에 추가한 시작 화면을 현재 공동 진입 화면으로 사용하고, app 어댑터가 그 화면의 `예상 견적 확인하기` 동작만 `/estimate`에 연결한다. Issue #12는 홈 UI를 새로 구현하거나 `/`를 `/estimate`로 자동 이동시키지 않는다. 알 수 없는 경로는 특정 flow CTA가 없는 공용 404를 표시한다.

## FE가 사용하는 API 계약

### Issue #12 기본 계약

- `POST /api/estimate-requests`
- `GET /api/operator/estimate-requests`
- `GET /api/operator/estimate-requests/{id}`
- `POST /api/operator/estimate-requests/{id}/manual-notification`

목록에는 이름·연락처가 없고 상세에만 있다. 문자 완료 API는 실제 SMS를 보내지 않는다. `transportFeasible` 값과 관계없이 저장 성공 시 상태는 `ESTIMATE_NOTIFIED`다.

DEC-020의 견적 FE MVP 적용안으로 생성 요청에 다음 동의 정보를 추가한다.

```json
{
  "name": "테스트사용자",
  "phoneNumber": "01000000000",
  "tradeArea": "테스트구 테스트동",
  "itemType": "테스트 의자",
  "highValueItem": false,
  "privacyConsent": true,
  "privacyPolicyVersion": "2026-08-06"
}
```

FE는 동의가 없으면 API를 호출하지 않는다. 견적 BE의 `privacyConsent` 재검증, server 동의 시각·정책 버전 저장과 `fieldErrors.privacyConsent` 계약은 아직 구현되지 않았으며 후속 협의에서 같은 계약으로 갱신한다. 그전에는 FE 화면과 요청 전달까지 구현된 상태이며 실제 개인정보 접수를 완료한 것으로 보지 않는다.

개인정보 안내는 사용자가 직접 입력하는 5개 항목과 요청 처리 중 생성·저장되는 동의 증적·요청 ID·상태·시각·운영 결과·실제 발송 문자 내용을 구분해 표시한다.

FE 안내는 `ESTIMATE_NOTIFIED` 완료 시점부터 30일 보관, 수동 철회·삭제와 비식별 통계만 잔존하는 적용안을 사용한다. 실제 삭제 집행과 증적은 후속 견적 BE·운영 계약이며 FE 체크박스로 완료되지 않는다.

상세의 완료 기록은 다음 응답을 기대한다.

```json
{
  "manualNotification": {
    "messageContent": "실제로 보낸 가상 문자 내용",
    "transportFeasible": true,
    "estimatedAmount": 30000,
    "notifiedAt": "2026-08-06T10:05:00+09:00"
  }
}
```

### 운영자 인증 계약 — 배차 BE 공용 규칙 채택

배차 BE의 공용 운영자 인터셉터 규칙을 견적 FE에도 사용한다. 배차 사용자 API·화면은 별도 배차 Issue에서 `develop`에 병합된 결과이며, 이 PR은 해당 flow를 수정하지 않고 공용 app에서 경로만 조합한다.

| 목적            | 요청·저장                                                   | FE 동작                                                           |
| --------------- | ----------------------------------------------------------- | ----------------------------------------------------------------- |
| 비밀번호 검증   | `GET /api/operator/auth` + `X-Operator-Secret`              | 입력값으로 먼저 요청하고 `authenticated: true`일 때만 저장, `401`이면 로그인 오류 |
| 탭 세션 유지    | `sessionStorage['setty.operatorSecret']`                    | 새로고침 동안 유지하고 탭이 닫히면 제거                           |
| 운영자 API 호출 | 모든 `/api/operator/**` 견적 GET·POST에 `X-Operator-Secret` | 저장한 값을 헤더로 첨부                                           |
| 로그아웃        | client 저장값 삭제                                          | server 요청 없이 `/operator/login`으로 이동                       |

server는 `SETTY_OPERATOR_SECRET` 환경 변수와 헤더를 비교하고 `/api/operator/**`를 보호한다. 모든 운영자 API의 `401`에서 FE는 저장값을 지우고 로그인 화면으로 이동한다. `/api/operator/session`, cookie와 `credentials: include`는 사용하지 않는다.

비밀번호는 번들에 하드코딩하거나 `localStorage`에 영구 저장하지 않는다. 다만 `sessionStorage` 값은 같은 탭의 JavaScript에서 읽을 수 있으므로 장기 인증이 아닌 빠른 MVP용 절충이다. 실제 DEV에서는 예시 비밀번호가 아닌 충분히 긴 값을 사용하고 HTTPS, XSS 방지, 비밀 공유·교체 절차를 확인한다.

FE는 개인정보가 포함될 수 있는 운영자 응답에 `cache: no-store`를 사용한다. BE와 reverse proxy도 `Cache-Control: no-store`를 반환하는지 실제 DEV에서 함께 확인한다.

현재 `develop`에는 배차 BE의 공용 운영자 인터셉터와 견적 API가 함께 병합되어 같은 `/api/operator/**` 경로의 견적 API도 보호한다. 다만 DEV 배포 후 `401`과 정상 헤더 요청을 실제 origin에서 통합 검증하기 전에는 운영자 보호 완료로 보지 않는다.

### API 주소와 CORS

- `SETTY_API_BASE_URL`은 프론트 빌드 시 주입하는 공개 API origin이다. 비밀값이 아니며 마지막 `/` 유무는 FE가 정규화한다.
- 로컬 개발에서 변수가 없으면 `http://localhost:8080`을 사용한다.
- production에서 변수가 없으면 사용자 기기의 `localhost`로 잘못 요청하지 않도록 현재 프론트와 같은 origin의 `/api/**`를 사용한다.
- FE·BE를 서로 다른 origin에 배포하면 `SETTY_API_BASE_URL`을 반드시 설정하고, BE CORS에 실제 FE origin을 정확히 허용한다. 예시는 `.env.example`에 있다.
- 공개 견적 생성과 운영자 요청 모두 cookie 인증을 사용하지 않는다. 운영자 요청에는 `X-Operator-Secret` 헤더만 추가한다.
- FE·BE가 다른 origin이면 BE CORS가 실제 FE origin, `GET`·`POST`·`OPTIONS`와 `X-Operator-Secret` 요청 헤더를 허용해야 한다.

## 화면 상태

- 사용자: 기본, 입력 오류, 제출 중, BE 필드 오류, 서버 실패, 접수 완료
- 운영자 로그인: 검증 중, 잘못된 비밀번호, 서버 실패
- 목록: 비밀번호 확인, 로딩, 빈 목록, 조회 실패, 정상 목록
- 상세: 로딩, 404, 조회 실패, 검토 대기 입력, 저장 중, `409`, 안내 완료 읽기 전용

## 의도적으로 제외

- 별도 공동 홈 신규 디자인, 사진 업로드, 사용자 견적 결과 조회, 요청 취소, 배차 요청 자동 연결
- 자동 가격 계산, 자동 SMS, 운송사 API, 자동 배차
- 문자 초안 저장, 페이지네이션, 복잡한 중복·재시도, 다중 운영자 수정
- 사용자 계정과 운영자 계정 관리
- `flows/dispatch/**` 변경과 미래 배차 운영 화면

## 공개 경계와 검토 포인트

- FE는 예상 견적 필수 동의·처리 안내·정책 버전 전달을 구현했다. 이름은 앞뒤 공백만 제거하고 내부 공백을 허용하며, 나이 입력·제한은 추가하지 않았다.
- 이름·연락처의 브라우저 자동완성을 실제 폼용 `name`·`tel`로 활성화했다. 자동화 테스트·로그·스크린샷에는 여전히 가상 데이터만 사용한다.
- 실제 사용자 DEV 접수 전에 BE 동의 증적·삭제 작업·수동 철회 절차, 실제 수신 문의 채널, 미완료 `PENDING_REVIEW` 최대 보관 기간, 배포 환경의 처리 내역과 운영자 API 보호를 확인한다.
- `setty@example.com`은 FE 구현을 위한 임시 문의처이며 실제 사용자가 접수하기 전 팀이 수신·처리할 수 있는 값으로 교체한다.
- 운영자 목록에 이름·연락처가 포함되면 계약 위반이다.
- 클라이언트에서 비밀번호를 직접 비교하거나 비밀번호를 번들·`localStorage`에 넣으면 보안 경계 위반이다.
- `sessionStorage`는 탭 유지용일 뿐 인증 경계가 아니다. server가 모든 운영자 API에서 헤더를 검증해야 한다.
- `ESTIMATE_NOTIFIED`인데 `manualNotification`이 없으면 server 응답 계약 불일치로 표시한다.
- API 경로나 응답이 바뀌면 임시 호환 코드를 추가하기 전에 Issue와 이 문서를 먼저 갱신한다.
- 작은 brand text는 기존 brand 색보다 진한 `#007b50`, brand 배경 버튼은 진한 글자를 써 대비를 확보한다. 공통 토큰을 바꾸지 않아 배차 화면에는 영향이 없다.
