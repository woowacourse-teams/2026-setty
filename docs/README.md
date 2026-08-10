# SETTY 문서 지도

## 최신 기준 후보

- `product/`: 워크북을 반영한 제품 기획·사용자 흐름·MVP 범위·용어 승인 후보
- `decisions/`: 결정 상태·이유·검증·재검토 조건
- `team/`: 협업 규칙, GitHub 흐름, 역할과 첫 개발 계획
- GitHub Issue: 각자가 실제로 구현할 결과·범위·완료 조건
- GitHub PR: 변경·검증·리뷰 기록

노션은 과거 기록과 회의 원본, 현재 담당 현황을 관리합니다. 이 브랜치의 문서는 팀 대조 검토와 PR 승인을 거쳐 `develop`에 병합된 뒤 최신 개발 기준이 됩니다. 병합 후 제품·개발 기준이 충돌하면 이 레포의 제품 문서와 결정 기록, GitHub Issue를 우선합니다.

## 읽는 순서

1. [제품 기획](product/product-brief.md)
2. [사용자·운영 흐름](product/user-operation-flow.md)
3. [MVP 범위](product/mvp-scope.md)
4. [결정 로그](decisions/DECISION-LOG.md)
5. [첫 개발 작업 계획](team/initial-development-plan.md)
6. [역할과 책임](team/roles-and-ownership.md)
7. [협업 규칙](team/collaboration-rules.md)
8. [GitHub 작업 흐름](team/github-workflow.md)
9. [용어사전](product/glossary.md): 용어가 헷갈릴 때 참고
10. [백엔드 배포](deployment.md): 배포 파이프라인과 EC2 준비 사항

## 현재 개발 흐름

- 예상 견적 요청: FE 1명·BE 1명
- 배차 요청: FE 1명·BE 2명
- 공통 환경: 한 명이 초기 설정하고 다섯 명이 새 clone에서 재현

예상 견적과 배차 요청은 별도 요청입니다. 가격·문자·운송 판단·실제 배차는 운영자가 수동 수행합니다.

## 변경 규칙

- 제품·운영 정책 변경은 관련 DEC와 제품 문서를 같은 PR에서 갱신합니다.
- 미정 정책을 코드로 먼저 결정하지 않습니다.
- 개인정보·운송 채널의 미검증 사항은 실제 사용자 공개와 가상 데이터 개발 중 무엇을 막는지 구분합니다.
- 과거 회의 워크북과 노션 내보내기는 이 레포에 커밋하지 않습니다.
