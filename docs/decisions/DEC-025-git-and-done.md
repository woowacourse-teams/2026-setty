# DEC-025 Git·병합·완료 기준

- 상태: **합의·실제 명령 검증 필요**
- 결정일: 2026-08-05
- 참여자: 캐리, 스마일, 밀란, 타스, 코코

## 결정

- `main`은 운영 기준, `develop`은 개발 통합 기준으로 사용한다.
- `main`과 `develop`에 직접 푸시하지 않는다.
- 작업 브랜치는 `develop`에서 만들고 `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, `refactor/<issue>-<slug>`를 사용한다.
- 기능·일반 수정 PR은 `develop`을 대상으로 한다.
- 작성자가 아닌 팀원 1명의 리뷰 후 **Merge commit**으로 병합한다.
- ~~첫 개발에서는 자동 배포를 만들지 않는다. Vercel 개발 환경과 DEV EC2에 수동으로 배포하고 절차를 재현한다.~~
  → [DEC-026](DEC-026-backend-auto-deploy.md)이 백엔드 DEV EC2에 한해 대체한다. 프론트 배포는 수동 절차를 유지한다.
- 설치·실행·테스트·빌드 명령은 실제 레포 설정 후 AGENTS.md와 README에 기록한다.

## 검토한 선택지

- Issue 브랜치 후 Squash
- Issue 브랜치 후 Merge commit
- Issue 브랜치 후 Rebase

## Done

“간단하면 바로 PR”은 제출 시점일 뿐 완료 기준이 아니다. 완료 조건·테스트·빌드·수동 확인·문서 영향·사람 리뷰를 충족하고 통합 결과가 재현될 때 Done이다.

## 영향·검증

- 긴급 연락 채널: Slack
- 담당: 전체, 명령 기록은 초기 레포 설정 담당과 각 영역 담당
- 기한: 공통 환경 Issue 종료 전
- 개발 차단: 실제 명령과 레포가 없으면 해당 카드 완료 불가
- 사용자 공개 차단: 자동화 부재 자체는 차단 아님. DEV 배포·운영 검증 실패는 차단
- 재검토: 두 흐름 통합 후, 충돌·리뷰 지연 또는 배포 실수 반복 시

## 연결·영향·대체

- 관련 문서: `docs/team/collaboration-rules.md`, `github-workflow.md`, `initial-development-plan.md`, 루트·하위 `AGENTS.md`
- 관련 Issue: 공통 환경과 견적 2개·배차 3개 Issue
- 팀 영향: 작업 묶음과 리뷰 이력을 Merge commit으로 보존
- 기술 영향: 실제 명령을 추측하지 않고 각 환경 카드에서 검증
- 대체: 과거 Squash 제안과 첫 개발을 수동 배포로만 진행한다는 결정
