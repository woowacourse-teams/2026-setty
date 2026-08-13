# SETTY GitHub 작업 흐름

- 상태: **문서 브랜치·Issue/PR 양식 반영, 라벨·보호 규칙 미적용**
- 목적: 복잡한 보드 없이 Issue와 PR만으로 첫 두 흐름을 진행한다.

## 1. GitHub에서 준비할 것

- `main`, `develop` 브랜치와 직접 푸시 금지
- Issue·PR 양식
- 아래 최소 라벨

| 용도 | 라벨 |
|---|---|
| 영역 | `area:frontend`, `area:backend`, `area:database`, `area:infra`, `area:docs` |
| 유형 | `type:feature`, `type:fix`, `type:decision`, `type:chore` |
| 상태 | `status:todo`, `status:blocked`, `status:review` |
| 위험 | `needs:decision`, `risk:privacy`, `risk:external-service` |

GitHub Project와 Milestone은 처음부터 만들지 않아도 된다. Issue가 많아져 목록만으로 보기 어려워질 때 추가한다.

## 2. 처음 만들 Issue 6개

| 순서 | Issue | 담당 |
|---:|---|---|
| 0 | 팀이 같은 방식으로 프론트·백엔드·MySQL을 실행할 수 있게 한다 | 초기 레포 설정 담당 |
| 1 | 예상 견적 요청과 운영자 확인 화면을 만든다 | 견적 FE |
| 2 | 예상 견적 요청을 저장하고 운영자에게 제공한다 | 견적 BE |
| 3 | 구매자·판매자·운영자 배차 요청 화면을 연결한다 | 배차 FE |
| 4 | 구매자 배차 요청과 판매자 입력 링크를 만든다 | 배차 BE1 |
| 5 | 판매자 입력을 저장하고 운영자에게 양쪽 정보를 제공한다 | 배차 BE2 |

Issue 상세 내용은 [`initial-development-plan.md`](initial-development-plan.md)에서 복사하고 실제 이름·검토자·API 링크만 채운다.

## 3. Issue 작성부터 병합까지

1. 담당자가 자기 Issue를 만든다.
2. 같은 흐름 동료와 검토자가 범위·완료 조건을 확인한다.
3. 필요한 선행 작업이 있으면 `status:blocked`, 없으면 `status:todo`를 붙인다.
4. `develop`에서 Issue 브랜치를 만든다.
5. 구현·테스트 후 `develop` 대상 PR을 만든다.
6. 지정 검토자가 확인한다.
7. Merge commit으로 병합한다.
8. 같은 흐름의 FE·BE가 전체 행동을 재현한 뒤 관련 Issue를 닫는다.

## 4. 브랜치

- `main`: 운영 기준
- `develop`: 개발 통합 기준
- `feature/<issue>-<slug>`
- `fix/<issue>-<slug>`
- `refactor/<issue>-<slug>`

`main`·`develop` 직접 푸시를 금지한다. 프론트 배포는 수동으로 유지한다. DEC-026 제안안은 `develop` 병합으로 백엔드 DEV CodePipeline을 시작하며, 팀 승인과 첫 성공 배포 전까지는 목표 상태로 관리한다.

## 5. 실제 GitHub에서만 확인할 것

- Issue·PR 양식 표시
- 라벨 존재와 적용
- Merge commit 허용
- `main`·`develop` 보호 규칙
- 실제 리뷰 승인 규칙

현재 로컬 문법 검증만으로 위 항목이 적용됐다고 표현하지 않는다.
