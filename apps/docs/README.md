# 배송원 앱 문서 (apps/docs)

`apps/` 아래 앱(현재 `apps/driver`) 개발에서만 쓰는 문서다. 저장소 루트 `docs/`(제품·결정·팀)와 분리되어 있으며, 앱 구현 관점의 아키텍처·API 매핑·미정 사항을 여기서 관리한다.

## 목차

- [architecture.md](./architecture.md) — 폴더 구조와 작성 규칙
- [api-mapping.md](./api-mapping.md) — 화면 ↔ 배차 API ↔ 타입 매핑
- [open-questions.md](./open-questions.md) — 아직 정해지지 않아 앱이 임시로 처리 중인 것들

## 앱 요약

- 대상: 배송원(기사). iOS 우선(팀 단말이 전부 iPhone).
- 스택: Expo + Expo Router + TypeScript, 스타일은 RN StyleSheet + `src/theme` 토큰.
- 데이터: 배차 API 연동. 베이스 URL이 없으면 목으로 동작(`src/api/mock`).
