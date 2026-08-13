# 백엔드 DEV EC2 자동 배포

근거 결정: [DEC-026](decisions/DEC-026-backend-auto-deploy.md)

## 1. 배포 흐름

```text
GitHub develop
  → CodePipeline Source (SourceArtifact)
  → CodePipeline Commands에서 Java 21 bootJar 빌드
  → BuildArtifact(app.jar, appspec.yml, 배포 스크립트)
  → AWS CodeDeploy
  → EC2 CodeDeploy Agent
  → systemd setty-backend.service
  → Spring Boot :8080
  → ALB Target Group :8080
```

- Docker와 Nginx를 사용하지 않는다.
- 기존 CodeBuild 프로젝트를 사용하지 않는다. Commands 작업의 CodePipeline 관리형 컴퓨팅에서 빌드한다.
- CodeDeploy Agent가 설치된 Amazon Linux 2023 ARM EC2에 JAR을 배포한다.
- 빌드 환경에는 운영 환경 변수를 넣지 않는다. 실제 값은 EC2의 `/opt/setty/setty.env`에서만 관리한다.
- MySQL 설치·시작·마이그레이션은 이 배포의 비범위다.

## 2. 배포 파일

| 파일 | 역할 |
|---|---|
| `appspec.yml` | CodeDeploy 파일 설치와 수명주기 훅 정의 |
| `server/deploy/scripts/before_install.sh` | Java 21, 전용 서비스 사용자, 환경 파일, 디스크 확인 |
| `server/deploy/scripts/after_install.sh` | 권한과 systemd 등록 |
| `server/deploy/scripts/stop.sh` | 이전 systemd 서비스 중지 |
| `server/deploy/scripts/start.sh` | 새 systemd 서비스 시작 |
| `server/deploy/scripts/validate.sh` | `127.0.0.1:8080/actuator/health` 검증 |
| `server/deploy/setty-backend.service` | 비로그인 사용자 `setty`로 JAR을 실행하는 systemd 유닛 |
| `server/deploy/setty.env.example` | EC2 전용 환경 파일 형식. 실제 값은 커밋하지 않음 |

EC2 배포 경로:

```text
/opt/setty/
├── setty.env            # root:root 0600, 사람이 최초 1회 생성
└── app/
    └── app.jar          # root:root 0644, 현재 실행 JAR
```

CodeDeploy 훅은 `root`로 설치 작업을 수행하지만 애플리케이션 프로세스는 `setty`로만 실행한다. `setty`는 비로그인·비관리자 계정이며 JAR과 환경 파일을 변경할 수 없다. systemd 유닛은 새 권한 획득, 홈 접근, 시스템 경로 쓰기와 Linux capability를 차단한다.

## 3. CodePipeline 설정

### BuildBackend

- 공급자: `Commands`
- 입력 아티팩트: `SourceArtifact`
- 출력 아티팩트 이름: `BuildArtifact`
- 빌드 환경 변수, VPC, 변수 네임스페이스: 설정하지 않음
- 출력 파일:
  - `appspec.yml`
  - `app.jar`
  - `server/deploy/**/*`

Commands:

```sh
set -euo pipefail
rpm --import https://yum.corretto.aws/corretto.key
curl -fsSL -o /etc/yum.repos.d/corretto.repo https://yum.corretto.aws/corretto.repo
yum install -y java-21-amazon-corretto-devel
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
chmod +x server/gradlew
./server/gradlew -p server --no-daemon --max-workers=1 clean bootJar -x test
mapfile -t BOOT_JARS < <(find server/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print)
test "${#BOOT_JARS[@]}" -eq 1
cp "${BOOT_JARS[0]}" app.jar
chmod 0755 server/deploy/scripts/*.sh
test -f appspec.yml
ls -lh app.jar appspec.yml
```

이 명령 블록이 BuildBackend 콘솔 값의 기준이다. 마지막 확인 당시 AWS 콘솔에는 고정 `x86_64` `JAVA_HOME`과 첫 JAR 선택 명령이 저장돼 있었으므로, 병합 전 위 내용으로 교체해 저장해야 한다. 저장소 파일만 병합해도 Commands 콘솔 값은 자동으로 바뀌지 않는다.

`bootJar`는 Spring 애플리케이션을 실행하지 않으므로 데이터베이스·웹훅·운영자 시크릿이 필요하지 않다. 현재 GitHub CI는 client만 검증하므로 서버 테스트가 별도 CI에서 자동 실행되지는 않는다. 배포용 JAR 생성은 `-x test`로 제한한다. 배포 파일만 바꾸는 PR은 AppSpec 파싱, 훅 문법 검사와 Commands와 같은 `clean bootJar -x test` 결과를 남긴다. 서버 애플리케이션 코드를 함께 바꾸는 PR은 `cd server && ./gradlew test && ./gradlew build`도 실행한다. 서버 테스트 CI 게이트는 이 결정의 비범위다.

### DeployBackend

- 공급자: `AWS CodeDeploy`
- 리전: `ap-northeast-2`
- 입력 아티팩트: `BuildArtifact`
- 애플리케이션: `setty-backend`
- 배포 그룹: `setty`
- 배포 유형: `In-place`
- 배포 구성: `CodeDeployDefault.AllAtOnce`
- 자동 롤백: 배포 실패 시 활성화
- 서비스 역할: `codedeploy-project`
- EC2 선택 태그: `DeployTarget=setty-backend-dev`

배포 그룹의 로드 밸런서 트래픽 제어가 켜져 있어야 `BlockTraffic`·`AllowTraffic` 단계가 실행된다. 태그 조건은 배포 시점에 정확히 한 대만 선택해야 한다. 최근 실패 배포에서는 두 대가 선택됐으므로 병합 전 다시 확인하고, 이전 EC2에 같은 태그가 남아 있다면 제거하는 것이 외부 선행 조건이다.

## 4. EC2 최초 1회 준비

필수 상태:

- Amazon Linux 2023 ARM. `ec2-user`는 유지보수 접속에만 사용하고, 배포 훅이 비로그인 사용자 `setty`를 생성해 애플리케이션을 실행함
- 인스턴스 프로파일 `ec2-project`
- CodeDeploy Agent가 `enabled`, `active`
- Java 21 설치
- EC2 태그 `DeployTarget=setty-backend-dev`
- 동일 태그를 가진 폐기·이전 EC2가 없음
- ALB 보안 그룹에서 EC2 보안 그룹의 TCP 8080 접근 허용
- 대상 그룹에 EC2의 포트 8080 등록
- 대상 그룹 헬스 체크 경로 `/actuator/health`
- 애플리케이션이 접근할 DEV MySQL 준비

확인 명령:

```sh
sudo systemctl is-enabled codedeploy-agent
sudo systemctl is-active codedeploy-agent
sudo systemctl status codedeploy-agent --no-pager
java -version
```

이 저장소는 Agent 설치 스크립트를 직접 관리하지 않는다. 새 인스턴스에서는 AWS의 [Amazon Linux·RHEL용 CodeDeploy Agent 설치 절차](https://docs.aws.amazon.com/codedeploy/latest/userguide/codedeploy-agent-operations-install-linux.html)를 사용하고, 위 명령으로 설치·자동 시작 상태를 확인한다. 현재 DEV EC2에는 Agent와 Java 21 설치가 완료돼 있다.

환경 파일 생성:

```sh
sudo install -d -o root -g root -m 0755 /opt/setty
sudo install -o root -g root -m 0600 /dev/null /opt/setty/setty.env
sudoedit /opt/setty/setty.env
```

`server/deploy/setty.env.example` 형식으로 다음 값을 입력한다.

```text
SETTY_OPERATOR_SECRET=
SETTY_FRONT_BASE_URL=https://www.setty.cloud
SETTY_DISCORD_DISPATCH_WEBHOOK_URL=
SETTY_DISCORD_ESTIMATE_WEBHOOK_URL=
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
```

`export`를 붙이지 않는다. 공백·`#` 등 systemd가 해석할 수 있는 문자가 포함된 값은 큰따옴표로 감싼다. 값 자체는 채팅, 로그, Git에 남기지 않는다.

`SPRING_PROFILES_ACTIVE`는 의도적으로 설정하지 않아 기본 프로필로 실행한다. 운영 빌드의 client가 상대 `/api` 경로를 사용하는 동일 출처 구성을 전제로 하며, `dev` 프로필은 `http://localhost:3000` CORS만 허용하므로 EC2에서 활성화하지 않는다. 프론트와 API를 서로 다른 출처로 분리할 경우 별도 결정과 CORS 설정 변경이 필요하다.

## 5. IAM

### EC2 인스턴스 프로파일 `ec2-project`

- 연결된 역할에 CodePipeline 아티팩트 객체의 `s3:GetObject` 권한. Agent가 `BuildArtifact`를 내려받는 데 사용한다.
- 연결된 역할에 애플리케이션 런타임 이미지 경로 `setty/images/items/*`의 `s3:PutObject` 권한. 현재 서버 코드는 업로드만 수행한다.
- 아티팩트 또는 이미지 버킷이 고객 관리형 KMS 키를 사용하면 해당 키의 `kms:Decrypt`·암호화 작업 권한도 최소 범위로 추가
- SSM을 사용할 경우 연결된 역할에 `AmazonSSMManagedInstanceCore`

CodeDeploy Agent가 실행 중인 것과 아티팩트 읽기 권한은 별개다. 첫 배포에서 `AccessDenied`가 발생하면 CodePipeline 아티팩트 버킷의 객체 ARN이 역할 정책 범위에 포함됐는지 확인한다.

### CodeDeploy 서비스 역할

배포 그룹 `setty`에 EC2 배포용 CodeDeploy 서비스 역할이 연결되어 있어야 한다. 이 역할과 EC2 인스턴스 역할은 서로 다르다.

## 6. 배포 수명주기

1. `BeforeBlockTraffic`~`AfterBlockTraffic`: 배포 그룹에 연결된 대상의 트래픽 차단
2. `ApplicationStop`: 이전 systemd 서비스 중지
3. `BeforeInstall`: Java 21·전용 사용자·환경 파일·디스크 확인
4. `Install`: 새 JAR과 systemd 유닛 설치
5. `AfterInstall`: 파일 권한과 systemd 등록
6. `ApplicationStart`: 새 JAR 시작
7. `ValidateService`: 최대 180초 동안 Actuator `UP` 확인
8. `BeforeAllowTraffic`~`AfterAllowTraffic`: 검증된 대상의 트래픽 재허용

검증 실패 시 현재 CodeDeploy 배포는 실패한다. 배포 그룹의 자동 롤백이 활성화돼 있으면 직전 성공 리비전이 다시 배포된다. 데이터베이스 스키마는 롤백하지 않는다.

`ApplicationStop`의 AppSpec과 스크립트는 현재 리비전이 아니라 직전 성공 리비전에서 실행되며, 인스턴스의 첫 배포에는 실행되지 않는다. 따라서 `ApplicationStart`는 단순 `start`가 아니라 `restart`를 사용해 기존 프로세스가 있더라도 새 JAR로 교체한다. CodeDeploy 롤백 역시 파일 백업을 복원하는 방식이 아니라 직전 성공 리비전을 새 배포로 다시 실행한다.

## 7. 장애 확인

```sh
sudo systemctl status setty-backend.service --no-pager
sudo journalctl -u setty-backend.service -n 200 --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
sudo systemctl status codedeploy-agent --no-pager
sudo journalctl -u codedeploy-agent -n 200 --no-pager
sudo tail -n 200 /var/log/aws/codedeploy-agent/codedeploy-agent.log
```

`CodeDeploy agent was not able to receive the lifecycle event`가 표시되면 Agent의 `active` 상태, 위 로그, 인스턴스 프로파일, CodeDeploy·S3로 나가는 네트워크 연결을 순서대로 확인한다. 배포 그룹이 `0 of 2 instances`처럼 의도보다 많은 대상을 선택하면 이전 EC2에 남은 `DeployTarget` 태그를 제거한 뒤 새 리비전으로 배포한다. 실패한 과거 리비전의 `Retry deployment`는 사용하지 않는다.

아직 실제 첫 배포와 롤백은 검증되지 않았다. 첫 성공 후 대상 그룹이 `Healthy`인지 별도로 확인한다.
