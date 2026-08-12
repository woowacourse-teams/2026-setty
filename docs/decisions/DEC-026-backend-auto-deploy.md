# DEC-026: 백엔드 DEV EC2 자동 배포

- 상태: 제안·팀 합의 필요
- 결정일: 2026-08-10
- 참여자: **[팀 확인 필요]**
- 관련 문서·Issue: `docs/deployment.md`, DEC-014, DEC-023, DEC-025, #73, #75
- 대체하는 결정: DEC-025의 "첫 개발에서는 자동 배포를 만들지 않는다" 항목
- 개정 이력:
  - 2026-08-10: 트리거 브랜치를 `main`에서 `develop`으로 수정
  - 2026-08-12: 실제 Ubuntu 직접 JVM 환경에 맞춰 CodeBuild·CodeDeploy·Docker 구성을 CodePipeline V2 Amazon EC2 배포 작업·SSM·systemd 구성으로 교체

## 결정할 문제

DEV EC2 수동 배포가 특정 담당자와 로컬 빌드 절차에 의존한다.
인바운드 보안 그룹을 변경하지 않고 `develop`의 백엔드를 반복 가능하게 배포할 경로가 필요하다.

## 확인된 사실

- 배포 대상은 기존 Ubuntu EC2 단일 인스턴스다.
- Spring Boot는 Docker가 아니라 `ssm-user`의 `nohup java -jar setty.jar`로 실행 중이다. 첫 systemd 배포에서 `ubuntu` 서비스로 이관한다.
- 기존 `redeploy.sh`는 EC2에서 Git pull, `clean bootJar`, 기존 프로세스 종료, 새 JAR의 nohup 실행을 수행한다.
- MySQL은 배포 대상 EC2에서 별도로 실행 중이며 이 배포가 생성하거나 제거하지 않는다.
- SSM Agent는 Ubuntu Snap 서비스에서 `enabled`, `active` 상태다. Systems Manager 관리형 노드의 `Online` 상태는 별도 확인이 필요하다.
- 인바운드 보안 그룹과 서브넷은 변경할 수 없다. EC2 아웃바운드는 허용한다.
- 재검토 당시 루트 파일시스템 여유 공간이 378 MiB뿐이었으나 EBS·파티션·ext4를 확장해 배포 최소치 2 GiB 이상을 확보했다.

## 선택지

### 선택지 A: 기존 수동 `redeploy.sh` 유지

- 장점: 인프라 변경이 없다.
- 단점: 배포자의 접속과 수동 명령에 의존한다. 프로세스 상태·재시작·로그 보존이 약하다.
- 되돌리기 비용: 없음

### 선택지 B: CodePipeline + CodeBuild + CodeDeploy

- 장점: 빌드 부하를 서비스 EC2와 분리한다. CodeDeploy의 표준 수명 주기를 사용한다.
- 단점: CodeBuild와 CodeDeploy 역할·에이전트가 추가된다. 실제 구성에서 CodeBuild 역할의 아티팩트 S3 읽기 권한 누락으로 Source 다운로드가 실패했다.
- 되돌리기 비용: 중간

### 선택지 C: CodePipeline V2 Amazon EC2 배포 작업 + SSM + systemd

- 장점: 기존 SSM 연결을 사용한다. CodeBuild와 CodeDeploy Agent가 필요 없다. systemd가 프로세스 재시작, 부팅 시 시작, 로그 수집을 담당한다.
- 단점: 빌드가 서비스 EC2의 CPU·메모리를 사용한다. 단일 인스턴스 교체라 다운타임이 있다.
- 되돌리기 비용: 낮음. 파이프라인을 중지하고 기존 수동 배포로 돌아갈 수 있다.

## 결정

**선택지 C 채택.** `develop` 푸시 시 다음 경로로 백엔드 DEV EC2를 배포한다.

```text
GitHub develop
→ CodePipeline Source
→ 비공개 S3 SourceArtifact
→ Amazon EC2 배포 작업
→ SSM
→ EC2에서 bootJar
→ systemd setty-backend.service 교체
→ Actuator 검증
```

범위:

- 백엔드 DEV EC2만 자동 배포한다.
- CodeBuild와 CodeDeploy를 사용하지 않는다.
- Docker 이미지와 컨테이너를 사용하지 않는다.
- EC2에서 JAR을 빌드한다. Gradle 힙은 512 MiB, worker는 1개로 제한한다.
- MySQL 설치·재시작·데이터 변경은 비범위다.
- 실제 비밀정보는 EC2의 `/opt/setty/setty.env`에서만 관리한다.
- systemd 서비스 사용자는 `ubuntu`다.
- 현재 JAR은 `/opt/setty/app/setty.jar`, 이전 JAR은 `/opt/setty/app/setty.jar.previous`다.
- 헬스 체크 실패 시 이전 JAR을 복구하지만 해당 파이프라인 실행은 실패로 기록한다.
- 무중단 배포는 하지 않는다.

## 결정 이유

Amazon EC2 배포 작업은 이미 구성한 SSM의 아웃바운드 연결을 사용하므로 인바운드 변경이 필요 없다.
현재 런타임이 Docker가 아닌 직접 JVM 실행이므로 systemd가 실제 환경과 일치한다.
CodeBuild·CodeDeploy를 제거하면 서비스 수와 IAM 실패 지점이 줄어든다.

## 부정·제약

- 빌드 중 서비스 EC2 자원 경합이 발생할 수 있다.
- 새 JAR로 교체하는 동안 요청 실패 구간이 생긴다.
- 첫 배포에서 기존 nohup 프로세스의 JAR 경로를 찾지 못하면 자동 롤백본이 없다.
- 애플리케이션 JAR만 롤백한다. 데이터베이스 스키마와 외부 상태는 롤백하지 않는다.
- CodePipeline과 IAM이 콘솔 관리 상태라 설정 변경이 코드 리뷰에 남지 않는다.
- `develop` 병합이 즉시 DEV 배포를 시작하므로 PR 리뷰가 배포 전 통제 지점이다.

## 검증

- `server/deployspec.yml` YAML 파싱
- 모든 `server/deploy/scripts/*.sh`의 `bash -n`
- Java 21에서 `server/gradlew clean bootJar -x test`
- Systems Manager 관리형 노드 `Online`
- EC2 역할로 SourceArtifact `s3:GetObject`
- 첫 배포 후 `systemctl is-active setty-backend.service`
- `/actuator/health`의 `UP`
- 두 번째 배포 실패를 유도한 뒤 이전 JAR 복구와 파이프라인 실패 상태 확인

## 재검토 조건

- 서비스 EC2의 빌드 자원 경합이 운영 요청에 영향을 줄 때
- PROD 환경 또는 무중단 배포가 필요할 때
- MySQL을 RDS로 이전하거나 스키마 마이그레이션 도구를 도입할 때
- 배포 실패가 반복되거나 JAR 한 개 롤백으로 복구되지 않을 때
