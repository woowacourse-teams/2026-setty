# 배차 API 매핑

배송원 앱이 사용하는 배차 API와 화면·타입 대응. 타입 정의는 `apps/driver/src/model/delivery.ts`.

## 타입 규칙

- `id`, 금액(`deliveryFee`): `number` (server `Long`)
- 타임스탬프(`requestedAt`, `acceptedAt`, `pickedUpAt`, `deliveredAt`): `Instant`(ISO 8601 문자열)로 받아 앱에서 파싱
- 그 외 값: `string`. **필드명은 명세 예시 그대로 유지**한다.
- `category`는 표시용 문자열 그대로 렌더한다(코드→한글 매핑표를 만들지 않음).

## 엔드포인트 ↔ 화면 ↔ 타입

| 화면 | 메서드 · 경로 | 응답 타입 | 비고 |
| --- | --- | --- | --- |
| 홈(요청 목록) | `GET /api/delivery/requests` | `DeliveryRequestSummaryResponse[]` | |
| 수신(단건 상세) | `GET /api/delivery/requests/{deliveryId}` | `DeliveryRequestDetailResponse` | 전화번호 없음, `requestedAt`만 |
| 수락 | `POST /api/delivery/requests/{deliveryId}/acceptance` | 없음(204) | 성공 후 목록 재조회 |
| 내 배차(목록) | `GET /api/delivery/shipments` | `ShipmentSummaryResponse[]` | 진행중/완료 탭은 앱이 status로 필터 |
| 내 배차(상세) | `GET /api/delivery/shipments/{deliveryId}` | `ShipmentDetailResponse` | 전화·타임스탬프 4종 |
| 수령 | `POST /api/delivery/shipments/{deliveryId}/pickup` | 없음(204) | `ACCEPTED`에서만 가능 |
| 배송완료 | `POST /api/delivery/shipments/{deliveryId}/completion` | 없음(204) | `PICKED_UP`에서만 가능 |

- shipments 경로의 `{id}`는 `deliveryId`와 같은 값이다.
- 수락·수령·완료 POST는 응답 바디가 없다. 성공하면 관련 목록/상세를 재조회해 화면을 맞춘다.
- 상태 전이 가드 위반은 **409 Conflict**다(예: `ACCEPTED`가 아닌데 수령 호출). 앱은 409 메시지를 안내하고 상세를 재조회해 서버 상태와 맞춘다.

## 필드명 불일치(명세대로 유지)

같은 '도착지'라도 응답마다 필드명이 다르다. 임의로 통일하지 않고 명세 그대로 둔다.

- 요청 요약: `deliveryAddress`
- 요청 상세: `destinationAddress`
- 배차 상세: `destination.address`

## DeliveryStatus

현재 `REQUESTED · ACCEPTED · PICKED_UP · DELIVERED` 4개. 확장될 수 있으며(취소·거절·실패 등 미확정) 확정 시 `model/delivery.ts`의 유니온과 `lib/statusMeta.ts`에 추가한다.

## 인증

`/api/delivery/*`는 향후 **UUID 헤더** 인증 예정(정확한 헤더 이름 미확정). 지금은 `lib/http.ts`의 `AUTH_HEADER` 상수와 `config.authUuid` 주입 지점만 열어 두고 실제로 붙이지 않는다. 로그인·회원가입 화면과 함께 다음 이슈에서 연동한다.
