# 배송원 앱 문서 (apps/docs)

`apps/` 아래 앱(현재 `apps/driver`) 개발에서만 쓰는 문서다. 저장소 루트 `docs/`(제품·결정·팀)와 분리되어 있으며, 앱 구현 관점의 아키텍처·API 매핑·미정 사항을 여기서 관리한다.

## 목차

- [architecture.md](./architecture.md) — 폴더 구조와 작성 규칙
- [api-mapping.md](./api-mapping.md) — 화면 ↔ 배차 API ↔ 타입 매핑
- [open-questions.md](./open-questions.md) — 아직 정해지지 않아 앱이 임시로 처리 중인 것들

## 앱 요약

- 대상: 배송원(기사). iOS 우선(팀 단말이 전부 iPhone).
- 스택: Expo + Expo Router + TypeScript, 스타일은 RN StyleSheet + `src/theme` 토큰.
- 인증: 배송원 로그인/회원가입(`/api/delivery/auth/*`) → `Authorization: Bearer` 토큰. SecureStore 저장, `Stack.Protected`로 화면 게이팅.
- 데이터: 배차 API 연동. 베이스 URL이 없으면 목으로 동작(`src/api/mock`).

## 실행 (실서버 연동)

1. 저장소 루트에서 서버 기동: `docker-compose up`(MySQL) + 서버 `bootRun`(포트 8080).
2. `apps/driver/.env.example`을 `.env`로 복사하고 `EXPO_PUBLIC_API_BASE_URL`을 채운다.
   - iOS 시뮬레이터: `http://localhost:8080`
   - 실기기(Expo Go/iPhone, 같은 Wi-Fi): `http://<개발 PC LAN IP>:8080`
3. `apps/driver`에서 `npm install` 후 `npm run ios`(또는 `npm start`).
4. 앱에서 회원가입 → 로그인 → 배차 요청/수락/수령/완료.
   - `.env`를 비우면 서버 없이 목 모드로 흐름만 확인할 수 있다.
