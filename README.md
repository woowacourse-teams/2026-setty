# SETTY

SETTY는 중고 가구·가전 거래 전에 예상 운송 가능 여부와 비용을 요청하고, 실제 거래에서는 구매자·판매자의 정보를 분리해 받아 운영자가 수동 배차를 조율하는 MVP입니다.

가격 조회, 문자 발송, 차량 판단, 운송사 접수와 예외 대응은 첫 MVP에서 운영자가 직접 수행합니다. 자동 가격 계산, 자동 문자, 운송사 API와 자동 배차를 구현하지 않습니다.

## Repository

```text
.
├─ client/     React·TypeScript·Webpack
├─ server/     Java·Spring Boot·Gradle
├─ docs/       제품·결정·협업 문서
└─ .github/    CI와 Issue·PR 양식
```

## Documentation

- [문서 지도](docs/README.md)
- [제품 기획](docs/product/product-brief.md)
- [사용자·운영 흐름](docs/product/user-operation-flow.md)
- [MVP 범위](docs/product/mvp-scope.md)
- [결정 로그](docs/decisions/DECISION-LOG.md)
- [첫 개발 작업 계획](docs/team/initial-development-plan.md)
- [역할과 책임](docs/team/roles-and-ownership.md)
- [백엔드 DEV 배포](docs/deployment.md)

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

첫 MVP 개발 환경은 MySQL과 JPA 스키마 자동 생성을 사용합니다. Flyway 도입 여부는 사용자 행동 검증 후 재검토합니다. 백엔드 DEV EC2는 [DEC-026](docs/decisions/DEC-026-backend-auto-deploy.md)에 따라 CodePipeline·CodeDeploy·systemd로 배포하며, 실제 환경 변수는 EC2에서만 관리합니다.

## Git workflow

- `main`, `develop` 직접 푸시 금지
- 작업 브랜치는 `develop`에서 생성
- `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, `refactor/<issue>-<slug>`, `chore/<slug>` 사용
- 작성자가 아닌 팀원 1명 리뷰
- Merge commit으로 병합
- DEC-026 제안안은 `develop` 병합으로 백엔드 DEV 배포를 시작하고, 프론트 배포는 수동으로 유지

실제 작업 범위와 완료 조건은 GitHub Issue를 기준으로 합니다.
