# 백엔드 DEV EC2 자동 배포

근거 결정: [DEC-026](decisions/DEC-026-backend-auto-deploy.md)

## 1. 채택한 흐름

```text
GitHub develop
  → CodePipeline Source
  → 비공개 S3 SourceArtifact
  → CodePipeline V2 Amazon EC2 배포 작업
  → SSM Agent가 명령 수신
  → /opt/setty/source에 소스 설치
  → EC2에서 server/gradlew clean bootJar
  → /opt/setty/app/setty.jar 교체
  → systemd setty-backend.service 시작
  → http://localhost:8080/actuator/health 검증
```

- CodeBuild를 사용하지 않는다. 빌드는 서비스 EC2에서 수행한다.
- CodeDeploy와 CodeDeploy Agent를 사용하지 않는다. Amazon EC2 배포 작업은 SSM을 사용한다.
- Docker를 사용하지 않는다. Spring Boot JAR을 systemd가 직접 실행한다.
- MySQL 설치·시작·마이그레이션은 이 배포의 비범위다. 현재 호스트의 MySQL을 유지한다.

## 2. 배포 파일

| 파일 | 역할 |
|---|---|
| `deployspec.yml` | 설치 경로와 배포 스크립트 정의. SourceArtifact 최상단에 둔다 |
| `scripts/before_install.sh` | Ubuntu 사용자, Java 21, 환경 파일 확인 |
| `scripts/build.sh` | EC2에서 Spring Boot 실행 JAR 빌드 |
| `scripts/start.sh` | 기존 nohup 프로세스 정리, JAR 교체, systemd 시작 |
| `scripts/validate.sh` | Actuator 검증, 실패 시 이전 JAR 복구 |
| `deploy/setty-backend.service` | systemd 서비스 정의 |
| `deploy/setty.env.example` | EC2 전용 환경 파일 예시. 실제 값은 커밋하지 않는다 |

배포 디렉터리:

```text
/opt/setty/
├── setty.env                 # 사람이 EC2에 생성, root:root 0600
├── source/                   # 현재 SourceArtifact
└── app/
    ├── setty.jar             # 현재 실행 JAR
    └── setty.jar.previous    # 직전 JAR
```

## 3. EC2 최초 1회 준비

### 필수 상태

- Ubuntu의 `ubuntu` 사용자 존재
- JDK 21 설치: `java`와 `javac` 모두 버전 21
- `curl`, `systemctl`, `runuser` 사용 가능
- SSM Agent `enabled`, `active`
- 애플리케이션이 사용할 MySQL 실행 중
- 인스턴스 프로파일 연결
- `/opt` 파일시스템 여유 공간 2 GiB 이상

확인 명령:

```sh
java -version
javac -version
df -h /opt
sudo snap services amazon-ssm-agent
```

Snap 출력이 다음 상태면 SSM Agent 설치 상태는 정상이다.

```text
amazon-ssm-agent.amazon-ssm-agent  enabled  active
```

단, AWS Systems Manager의 **관리형 노드** 화면에서 인스턴스가 `Online`이어야 실제 명령 수신도 검증된다.

### 환경 파일

```sh
sudo install -d -o root -g root -m 0755 /opt/setty
sudo install -o root -g root -m 0600 /dev/null /opt/setty/setty.env
sudoedit /opt/setty/setty.env
```

내용은 `deploy/setty.env.example` 형식을 사용한다. `export`를 붙이지 않는다.
기존 `redeploy.sh`에 있던 네 개의 환경 변수 값을 옮긴다. 실제 비밀값은 레포에 커밋하지 않는다.

## 4. IAM

### EC2 인스턴스 역할

필수 권한:

- `AmazonSSMManagedInstanceCore`
- 아티팩트 객체에 대한 `s3:GetObject`

현재 아티팩트 버킷 전체를 임시로 허용할 때의 리소스 예시:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::techcourse-project-2026-artifacts/*"
    }
  ]
}
```

실제 키 prefix가 고정되면 리소스를 해당 prefix로 축소한다. 고객 관리형 KMS 키로 아티팩트를 암호화했다면 그 키의 `kms:Decrypt`도 필요하다.
애플리케이션 런타임의 이미지 S3 업로드 권한은 배포 아티팩트 읽기 권한과 별도다.

### CodePipeline 서비스 역할

Amazon EC2 배포 작업을 위해 대상 조회와 SSM 명령 실행 권한이 필요하다. 최소한 다음 작업을 허용하는지 확인한다.

- EC2 인스턴스·태그 조회
- `ssm:SendCommand`
- SSM 명령 결과 조회
- 배포 로그용 CloudWatch Logs 접근

## 5. CodePipeline 설정

- 파이프라인 유형: V2
- 실행 모드: `QUEUED`
- Source: GitHub `develop`
- Source 출력: `SourceArtifact`
- Deploy 공급자: `Amazon EC2`
- 입력 아티팩트: `SourceArtifact` 하나만 선택
- 인스턴스 유형: Amazon EC2
- 대상: 배포 전용 태그로 **한 인스턴스만** 일치
- 배포 사양: **DeploySpec 파일 사용**
- DeploySpec 경로: `deployspec.yml`

`BuildArtifact`는 사용하지 않는다. Build 스테이지도 두지 않는다.
작업 구성의 대상 디렉터리와 스크립트 칸을 직접 입력하는 방식도 사용하지 않는다. 경로와 실행 순서의 기준은 `deployspec.yml` 하나로 유지한다.

## 6. 실행 순서

1. `BeforeDeploy`: 필수 명령, Java 21, `/opt/setty/setty.env` 확인 후 이전 소스 정리
2. 파일 설치: SourceArtifact를 `/opt/setty/source`에 덮어쓰기
3. `AfterDeploy`: 기존 앱을 실행한 채 새 JAR을 `setty.jar.next`로 빌드
4. `AfterDeploy`: 기존 JAR 보존, 기존 systemd 또는 nohup 앱 종료, 새 JAR로 교체, systemd 시작
5. `AfterDeploy`: 최대 180초 동안 Actuator 확인

첫 systemd 배포에서는 `ubuntu` 또는 `ssm-user`가 실행한 기존 `nohup java -jar setty.jar` 프로세스를 JAR 이름으로 찾아 종료한다.
기존 프로세스가 실행한 JAR 경로를 확인할 수 있으면 `setty.jar.previous`로 보존한다.
이후 배포부터는 `setty-backend.service`만 제어한다.
첫 전환 후 기존 `redeploy.sh`를 다시 실행하지 않는다. 다시 실행하면 systemd 관리에서 벗어난 nohup 프로세스가 생성된다.

헬스 체크 실패 시 이전 JAR을 복구하고 서비스를 다시 시작한다. 복구에 성공해도 현재 파이프라인 실행은 실패로 남긴다.

## 7. 검증과 장애 확인

```sh
sudo systemctl status setty-backend.service --no-pager
sudo journalctl -u setty-backend.service -n 200 --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
```

SSM 실행 로그는 Systems Manager의 Run Command 실행 기록과 CodePipeline 배포 작업 상세에서 확인한다.

검증 상태(2026-08-12):

- Java 21 `clean bootJar -x test`: 통과. 실행 가능한 Spring Boot JAR 1개 생성
- `deployspec.yml` YAML 파싱: 통과
- 모든 배포 셸의 `bash -n`: 통과
- EC2 `/opt` 파일시스템 여유 공간 2 GiB 이상: 사용자 검증 완료
- `/opt/setty/setty.env`의 네 개 `SETTY_*` 변수와 `root:root 0600` 권한: 사용자 검증 완료
- 실제 CodePipeline → SSM → Ubuntu 배포: **확인 필요**
- 첫 nohup → systemd 전환과 실제 이전 JAR 롤백: **확인 필요**

## 8. 제약

- 서비스 EC2에서 빌드하므로 배포 중 CPU·메모리를 추가 사용한다. Gradle 힙을 512 MiB, worker를 1개로 제한한다.
- 재검토 중 루트 파일시스템 여유가 378 MiB뿐인 문제를 발견했다. EBS·파티션·ext4 확장 후 최소 2 GiB 이상을 확보했다.
- `bootJar`만 수행하며 서버 테스트는 배포 게이트에 포함하지 않는다.
- 단일 인스턴스의 JAR 교체이므로 짧은 다운타임이 있다.
- 데이터베이스 변경 롤백은 수행하지 않는다. 이전 JAR과 호환되지 않는 스키마 변경은 별도 절차가 필요하다.
- `BeforeDeploy`가 `/opt/setty/source`의 기존 내용만 삭제한다. `/opt/setty/app`의 현재·이전 JAR과 `/opt/setty/setty.env`는 삭제하지 않는다.
