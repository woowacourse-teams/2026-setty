# 미정 사항 (앱이 임시로 처리 중)

명세가 확정되지 않아 앱이 잠정 처리한 지점들이다. 백엔드·기획과 확정되면 여기와 코드를 함께 갱신한다.

## 1. 인증 — 해결됨(#195)

- 배송원 전용 회원가입·로그인(`/api/delivery/auth/signup`·`login`)이 서버에 추가되고, 배차 API 인증 주체가 배송원(delivery_member)으로 전환됨(PR #190·#194).
- 앱 처리: `Authorization: Bearer <token>`(로그인 응답 토큰) 방식으로 확정. 토큰은 SecureStore(`lib/tokenStore.ts`)에 저장, `lib/http.ts`가 주입. `401 INVALID_TOKEN`이면 전역 로그아웃. 로그인·회원가입 화면 추가(`features/auth`). (이전의 `X-User-Id`/`authUuid` 잠정안은 폐기.)
- 상세: `apps/docs/api-mapping.md`의 인증 섹션 참고.

## 2. 거절 API

- 상태: 대응 API 없음.
- 임시 처리: 수신 상세의 '거절'은 **로컬에서만 숨김**(`features/requests/rejectedStore.ts`). 서버에는 반영하지 않으며, 앱을 완전히 종료하면 초기화된다. 홈 카드의 스와이프-거절 제스처는 이번 범위에서 제외(버튼으로 대체).
- 확정 필요: 거절 엔드포인트 유무와, 거절 시 요청이 목록에서 사라지는지 여부.

## 3. 정산 API

- 상태: 미정(전용 API 없음).
- 현재 처리: 정산 값(오늘 수입·완료 건수·건당 평균)을 `GET /api/delivery/shipments`의 `DELIVERED` 건에서 **앱이 계산**한다(`features/settlement`). '오늘' 필터는 아직 없이 완료 건 전체를 합산한다(요약 응답에 `deliveredAt`이 없음). 전용 정산 API 또는 기간 필터가 생기면 교체한다.

## 4. 목록 응답 봉투

- 상태: 미확정. `GET .../requests`(목록)가 bare 배열인지 `{ "items": [...] }`인지 명세에 없다.
- 임시 처리: `lib/http.ts`의 `httpGetList`가 **두 형태를 모두 허용**한다. 확정되면 좁힌다.

## 5. 배차 API dev 베이스 URL — 확정

- 로컬 서버는 포트 `8080`(저장소 루트 `docker-compose up`으로 MySQL 기동 + `bootRun`).
- 앱: iOS 시뮬레이터 `http://localhost:8080`, 실기기 `http://<PC LAN IP>:8080`. `.env`의 `EXPO_PUBLIC_API_BASE_URL`로 주입(`.env.example` 참고). 비우면 목 모드.

## 6. 내 배차 상세 메서드

- 참고: 상세 조회는 `GET /api/delivery/shipments/{deliveryId}`로 정리했다(초안에서 POST로 표기된 적 있음 → GET으로 확정).

## 7. 수락 경로 `/acceptance` 제거 대기 ⚠️

- 합의: 수락은 서브패스 없이 `POST /api/delivery/requests/{deliveryId}`. 앱은 이 형태로 구현돼 있다.
- 현재 서버(develop `DeliveryController`)에는 아직 `POST /api/delivery/requests/{deliveryId}/acceptance`가 남아 있어, 서버에서 제거가 머지되기 전까지 **수락만 404**가 난다(목록·상세·수령·완료는 정상).
- 서버 제거 반영 시 별도 조치 불필요(앱은 이미 대상 경로 사용).
