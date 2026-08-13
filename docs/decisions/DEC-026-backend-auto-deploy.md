# DEC-026: 백엔드 DEV EC2 자동 배포

- 상태: 제안·팀 합의 필요
- 결정일: 2026-08-10
- 참여자: **[팀 확인 필요]**
- 관련 문서·Issue: `docs/deployment.md`, DEC-014, DEC-023, DEC-025, #73, #75, #127
- 대체하는 결정: DEC-025의 "첫 개발에서는 자동 배포를 만들지 않는다" 항목
- 개정 이력:
  - 2026-08-10: 트리거 브랜치를 `develop`으로 수정
  - 2026-08-12: Ubuntu EC2 직접 배포안을 기록
  - 2026-08-13: 실제 신규 Amazon Linux 2023 EC2와 AWS 권한에 맞춰 Commands·CodeDeploy·systemd 구성으로 교체

## 결정할 문제

DEV EC2 수동 배포가 특정 담당자와 로컬 빌드 절차에 의존한다. `develop`의 백엔드를 반복 가능하게 빌드하고 새 EC2에 배포할 경로가 필요하다.

## 확인된 사실

- 의도한 대상은 Amazon Linux 2023 ARM `t4g.small` 단일 EC2다. 최근 실패 배포에서는 두 대가 선택됐으므로 병합 전 태그 조건이 정확히 한 대만 선택하는지 다시 확인해야 한다.
- EC2에는 `ec2-project` 인스턴스 프로파일을 사용한다.
- CodeDeploy 애플리케이션 `setty-backend`와 배포 그룹 `setty`가 구성돼 있다.
- EC2에 CodeDeploy Agent와 Java 21을 설치했다.
- ALB와 EC2는 TCP 8080으로 직접 연결한다.
- 기존 CodeBuild 프로젝트는 역할의 SourceArtifact S3 읽기 권한 부족으로 다운로드에 실패했다.
- 실제 런타임 비밀값은 저장소에 커밋할 수 없다.

## 선택지

### 선택지 A: 수동 배포 유지

- 장점: 추가 구성이 없다.
- 단점: 접속과 수동 명령, 담당자 환경에 의존한다.

### 선택지 B: 기존 CodeBuild 프로젝트 + CodeDeploy

- 장점: 일반적인 빌드·배포 분리 구조다.
- 단점: 현재 CodeBuild 역할이 CodePipeline SourceArtifact를 읽지 못하며 해당 역할 정책을 직접 수정하기 어렵다.

### 선택지 C: CodePipeline Commands + CodeDeploy + systemd

- 장점: 기존 CodeBuild 프로젝트 역할을 사용하지 않고 빌드 부하를 서비스 EC2와 분리한다. CodeDeploy 표준 수명주기와 Agent를 사용한다.
- 단점: Commands의 관리형 컴퓨팅 비용이 발생하고 콘솔 설정 일부가 코드 리뷰에 남지 않는다.

## 결정

**이 PR에서 선택지 C를 제안한다. 팀 합의 후 상태와 참여자를 갱신한다.**

```text
GitHub develop
→ CodePipeline Source
→ Commands에서 Java 21 bootJar
→ BuildArtifact
→ AWS CodeDeploy
→ EC2 CodeDeploy Agent
→ systemd setty-backend.service
→ Spring Boot :8080
→ ALB
```

범위:

- 백엔드 DEV EC2만 자동 배포한다.
- Docker와 Nginx를 사용하지 않는다.
- Commands 빌드에는 런타임 환경 변수를 주입하지 않는다.
- 실제 비밀정보와 DB 접속 정보는 EC2의 `/opt/setty/setty.env`에서만 관리한다.
- systemd 서비스는 비로그인 전용 사용자 `setty`로 실행한다. 배포 파일과 JAR은 `root`만 변경할 수 있다.
- MySQL 설치·재시작·데이터 변경은 비범위다.
- 단일 인스턴스이므로 짧은 다운타임을 허용한다.

## 결정 이유

Commands 작업은 CodePipeline 관리형 컴퓨팅을 사용하므로 권한을 변경하기 어려운 기존 CodeBuild 프로젝트 역할을 우회한다. 빌드가 애플리케이션 EC2 자원을 사용하지 않으며, CodeDeploy가 이미 구성한 애플리케이션·배포 그룹·Agent를 그대로 사용한다. 런타임이 직접 JVM 실행이므로 systemd가 프로세스 재시작과 부팅 시 시작, 로그 수집을 담당한다.

## 부정·제약

- `develop` 변경이 DEV 배포를 유발하므로 PR 리뷰가 배포 전 통제 지점이다.
- 배포 중 짧은 요청 실패 구간이 생길 수 있다.
- 새 EC2가 사용할 데이터베이스가 준비되지 않으면 Actuator 검증에 실패한다.
- CodeDeploy 롤백은 JAR과 배포 파일만 되돌리며 데이터베이스 스키마를 되돌리지 않는다.
- EC2 역할이 CodePipeline 아티팩트 객체를 읽지 못하면 `DownloadBundle`에서 실패한다.
- 배포 대상 태그가 여러 EC2에 남아 있으면 의도하지 않은 인스턴스까지 함께 배포된다.

## 검증

- `appspec.yml` YAML 파싱
- 모든 `server/deploy/scripts/*.sh`의 `bash -n`
- Java 21에서 Commands와 같은 `clean bootJar -x test`
- CodeDeploy Agent `active`
- EC2 역할의 BuildArtifact `s3:GetObject`
- 첫 배포 후 `systemctl is-active setty-backend.service`
- `127.0.0.1:8080/actuator/health`의 `UP`
- ALB 대상 그룹의 `Healthy`
- 실패 리비전 배포 후 CodeDeploy 자동 롤백

## 재검토 조건

- PROD 또는 무중단 배포가 필요할 때
- DB 스키마 마이그레이션 도구를 도입할 때
- 단일 EC2 장애가 서비스 가용성 요구를 충족하지 못할 때
- AWS 리소스를 IaC로 관리할 때
