# SETTY

SETTY는 중고 가구·가전 거래 전에 예상 운송 가능 여부와 비용을 요청하고, 실제 거래에서는 구매자·판매자의 정보를 분리해 받아 운영자가 수동 배차를 조율하는 MVP입니다.

가격 조회, 문자 발송, 차량 판단, 운송사 접수와 예외 대응은 첫 MVP에서 운영자가 직접 수행합니다. 자동 가격 계산, 자동 문자, 운송사 API와 자동 배차를 구현하지 않습니다.

## Repository

```text
.
├─ client/     React·TypeScript·Webpack
├─ apps/       배송원 앱 및 전용 문서
├─ server/     Java·Spring Boot·Gradle
├─ docs/       도메인·서비스 흐름 정책
└─ .github/    CI와 Issue·PR 양식
```

## Documentation

- [살아있는 정책 문서 운영 가이드](docs/policy/policy-guide.md)

## Client

요구 사항: Node.js 20 이상, npm

```bash
cd client
npm ci
npm run dev
```

검증:

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

## Server

요구 사항: JDK 21

```bash
cd server
./gradlew bootRun
```

검증:

```bash
./gradlew test
./gradlew build
```

개발 환경은 MySQL을 사용합니다. 스키마는 [schema.sql](server/src/main/resources/schema.sql)로 관리하고, JPA는 실행 시 스키마 일치 여부를 검증합니다.

## Git workflow

- `main`, `develop` 직접 푸시 금지
- 작업 브랜치는 `develop`에서 생성
- `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, `refactor/<issue>-<slug>`, `chore/<slug>` 사용
- 작성자가 아닌 팀원 1명 리뷰
- Merge commit으로 병합

실제 작업 범위와 완료 조건은 GitHub Issue를 기준으로 합니다.
