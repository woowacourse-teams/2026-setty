# 배차 API 매핑

배송원 앱이 사용하는 배차·인증 API와 화면·타입 대응. 타입 정의는 `apps/driver/src/model/delivery.ts`, `apps/driver/src/model/auth.ts`.

## 인증 엔드포인트 (공개)

`/api/delivery/auth/signup`, `/api/delivery/auth/login` 두 개만 인증이 필요 없다. 그 외 `/api/delivery/**`는 전부 로그인 토큰이 필요하다.

| 화면 | 메서드 · 경로 | 요청 · 응답 | 비고 |
| --- | --- | --- | --- |
| 회원가입 | `POST /api/delivery/auth/signup` | `SignupRequest` → `SignupResponse`(201) | `{ id, loginId }` |
| 로그인 | `POST /api/delivery/auth/login` | `LoginRequest` → `LoginResponse`(200) | `{ token }`, 로그인마다 회전하는 UUID |

회원가입 검증 규칙(서버와 동일, `features/auth/validators.ts`):

| 필드 | 규칙 |
| --- | --- |
| `loginId` | `^[a-z0-9]{4,20}$` |
| `password` | 8~64자 |
| `phoneNumber` | `^010-\d{4}-\d{4}$` |
| `licensePlateNumber` | `^\d{2,3}[가-힣]\d{4}$` (예: 12가3456) |
| `carType` | 1~30자 |
| `businessRegistrationNumber` | `^\d{3}-\d{2}-\d{5}$` (예: 123-01-56789) |

## 타입 규칙

- `id`, 금액(`deliveryFee`): `number` (server `Long`)
- 타임스탬프(`requestedAt`, `acceptedAt`, `pickedUpAt`, `deliveredAt`): `Instant`(ISO 8601 문자열)로 받아 앱에서 파싱
- 그 외 값: `string`. **필드명은 명세 예시 그대로 유지**한다.
- `category`는 표시용 문자열 그대로 렌더한다(코드→한글 매핑표를 만들지 않음).

## 엔드포인트 ↔ 화면 ↔ 타입

| 화면 | 메서드 · 경로 | 응답 타입 | 비고 |
| --- | --- | --- | --- |
| 홈(요청 목록) | `GET /api/delivery/requests` | `DeliveryRequestSummaryResponse[]` | |
| 홈(요청 목록 변경) | `GET /api/delivery/requests/events` | SSE `delivery-requests-changed` | 이벤트 수신·연결 재수립 시 목록 재조회 |
| 수신(단건 상세) | `GET /api/delivery/requests/{deliveryId}` | `DeliveryRequestDetailResponse` | 전화번호 없음, `requestedAt`만 |
| 수락 | `POST /api/delivery/requests/{deliveryId}` | 없음(204) | 서브패스 `/acceptance` 없음(합의). ⚠️ 서버 제거 대기 — 아래 참고 |
| 내 배차(목록) | `GET /api/delivery/shipments` | `ShipmentSummaryResponse[]` | 진행중/완료 탭은 앱이 status로 필터 |
| 내 배차(상세) | `GET /api/delivery/shipments/{deliveryId}` | `ShipmentDetailResponse` | 전화·타임스탬프 4종 |
| 수령 | `POST /api/delivery/shipments/{deliveryId}/pickup` | 없음(204) | `ACCEPTED`에서만 가능 |
| 배송완료 | `POST /api/delivery/shipments/{deliveryId}/completion` | 없음(204) | `PICKED_UP`에서만 가능 |

- shipments 경로의 `{id}`는 `deliveryId`와 같은 값이다.
- 수락·수령·완료 POST는 응답 바디가 없다. 성공하면 관련 목록/상세를 재조회해 화면을 맞춘다.
- 상태 전이 가드 위반은 **409 Conflict**다(예: `ACCEPTED`가 아닌데 수령 호출). 앱은 409 메시지를 안내하고 상세를 재조회해 서버 상태와 맞춘다.
- SSE는 변경 신호만 보낸다. 앱은 foreground에서만 연결하고, 끊기면 1~30초 간격으로 재연결한다.

## 필드명 불일치(명세대로 유지)

같은 '도착지'라도 응답마다 필드명이 다르다. 임의로 통일하지 않고 명세 그대로 둔다.

- 요청 요약: `deliveryAddress`
- 요청 상세: `destinationAddress`
- 배차 상세: `destination.address`

## DeliveryStatus

현재 `REQUESTED · ACCEPTED · PICKED_UP · DELIVERED` 4개. 확장될 수 있으며(취소·거절·실패 등 미확정) 확정 시 `model/delivery.ts`의 유니온과 `lib/statusMeta.ts`에 추가한다.

## 인증 헤더 · 에러

- 배차 API의 인증 주체는 **배송원(delivery_member)** 이다. 반드시 `/api/delivery/auth/login`으로 받은 토큰을 쓴다(플랫폼 로그인 토큰 아님).
- 헤더: `Authorization: Bearer <token>`. `lib/tokenStore.ts`에 저장(SecureStore)하고 `lib/http.ts`가 요청마다 주입한다.
- `driverId`는 서버가 토큰 → `delivery_member.id`로 판단하므로 앱이 보내지 않는다.
- 토큰 없음/무효 → `401 { code: "INVALID_TOKEN" }`. `http.ts`가 이를 감지해 전역 로그아웃(→ 로그인 화면)으로 유도한다.

### 에러 코드 → 문구 (`lib/errorMessage.ts`)

서버 공통 형식 `{ code, message }`. 아래 코드는 사용자 문구로 매핑한다.

| code | 상황 |
| --- | --- |
| `DUPLICATE_LOGIN_ID` (400) | 아이디 중복 |
| `LOGIN_FAILED` (401) | 로그인 실패 |
| `INVALID_REQUEST` (400) | 검증 실패(서버 `"<필드>: <메시지>"` 문구를 그대로 노출) |
| `INVALID_TOKEN` (401) | 토큰 없음/무효 |
| `DELIVERY_NOT_FOUND` (404) | 배차 없음 |
| `DELIVERY_DRIVER_MISMATCH` (404) | 내 배차 아님 |
| `DELIVERY_ALREADY_ACCEPTED` (409) | 이미 수락된 배차 |
| `ORDER_DELIVERY_STATUS_MISMATCH` (409) | 처리 불가한 상태 |

## 목(mock) 모드

`EXPO_PUBLIC_API_BASE_URL`이 비어 있으면 목으로 동작한다(`config.useMock`). 목 모드에서는 인증도 목 처리(`api/mock/authMock.ts`)되어 아무 값으로나 로그인해 화면 흐름을 볼 수 있다. 실서버 연동은 `.env`에 베이스 URL을 채우면 전환된다(`.env.example` 참고).
