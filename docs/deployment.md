# 백엔드 DEV EC2 자동 배포

근거 결정: [DEC-026](decisions/DEC-026-backend-auto-deploy.md)

## 1. 흐름

```
GitHub main 푸시
  → CodePipeline (Source)
  → CodeBuild        buildspec.yml   : ./gradlew bootJar → app.jar
  → S3 아티팩트
  → CodeDeploy       appspec.yml     : EC2 In-place
       BeforeInstall     scripts/before_install.sh  사전 점검
       ApplicationStart  scripts/start.sh           docker build → 앱 컨테이너 교체
       ValidateService   scripts/validate.sh        actuator health 확인
```

MySQL 컨테이너는 배포 대상이 아니다. `start.sh`는 `--no-deps`로 앱만 재생성하고 `db-data` 볼륨을 유지한다.

## 2. 레포에 있는 파일

| 파일 | 역할 |
|---|---|
| `buildspec.yml` | CodeBuild가 JAR을 만들고 `app.jar`로 이름을 고정한다 |
| `Dockerfile` | `app.jar`만 담는 런타임 이미지. 레포에는 `app.jar`가 없어 배포 번들 안에서만 빌드된다 |
| `appspec.yml` | CodeDeploy 훅 정의. **반드시 번들 최상단에 있어야 한다** |
| `docker-compose.prod.yml` | DEV EC2용 `db` + `app`. 로컬 개발은 `docker-compose.yml`을 쓴다 |
| `scripts/*.sh` | 배포 훅 |

`ApplicationStop` 훅은 직전 성공 배포의 스크립트로 실행되어 첫 배포에서는 돌지 않는다.
그래서 기존 컨테이너 정리를 전부 `ApplicationStart`(`start.sh`)에 넣었다. 이 배치를 바꾸지 말 것.

## 3. EC2에 사람이 직접 만들어 둘 것

### `/opt/setty/.env`

레포에 커밋하지 않는다. `chmod 600`, 소유자 root로 둔다.

```sh
# MySQL 컨테이너 초기화용. 볼륨이 비어 있는 첫 기동에만 반영된다.
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=setty
MYSQL_USER=setty
MYSQL_PASSWORD=

# application.yml의 spring.datasource.* 를 덮어쓴다.
# 호스트명은 localhost가 아니라 compose 서비스명 db다.
SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/setty?characterEncoding=UTF-8&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false
SPRING_DATASOURCE_USERNAME=setty
SPRING_DATASOURCE_PASSWORD=

# 앱 설정
SETTY_OPERATOR_SECRET=
SETTY_FRONT_BASE_URL=https://<CloudFront 도메인>
```

`MYSQL_PASSWORD`와 `SPRING_DATASOURCE_PASSWORD`는 같은 값이어야 한다.

### 그 외

- Docker Engine과 `docker compose` 플러그인
- CodeDeploy 에이전트 (실행 중이어야 한다)
- `/opt/setty/app` 디렉토리 (CodeDeploy가 번들을 푸는 위치)

## 4. AWS 콘솔에서 설정할 것

- **CodeBuild**: 이미지에 Java 21(`corretto21`) 런타임이 있는 표준 이미지. 아티팩트는 S3.
- **CodeDeploy**: 애플리케이션 + 배포 그룹(In-place). 대상은 `ec2-setty-1` (`i-0634a9c06aa1a2fc4`).
- **CodePipeline**: Source(GitHub, `main`) → Build(CodeBuild) → Deploy(CodeDeploy).
- **IAM**
  - EC2 인스턴스 프로파일 `ec2-project`: 아티팩트 S3 버킷에 `s3:GetObject`. 에이전트가 번들을 받는 데 필요하다.
  - CodeBuild 서비스 역할: 아티팩트 S3 쓰기, CloudWatch Logs
  - CodeDeploy 서비스 역할: `AWSCodeDeployRole`
- **CloudFront**: `/api/*` behavior가 ALB origin을 향하고, 캐시 비활성 + 전체 HTTP 메서드 허용이어야 한다.
  프론트가 API를 상대 경로로 호출하므로(`client/src/shared/api/http.ts:5`) 이 behavior가 없으면 CORS가 아니라 404·405가 난다.
- **EC2 metadata hop limit**: 기본값 1이면 컨테이너 안에서 IMDS에 닿지 못해 S3 업로드만 실패한다. 2 이상으로 둔다.

## 5. 현재 검증 상태

AGENTS.md에 따라 실제로 돌려본 것과 아닌 것을 구분해 적는다.

**로컬 Docker에서 검증함** (2026-08-10)

`docker-compose.prod.yml`의 `env_file` 경로만 임시 파일로 바꿔 실제로 기동해 확인했다.

- `./server/gradlew -p server --no-daemon clean bootJar` → JAR 생성 (actuator 추가 후)
- `docker build -f Dockerfile .` → 이미지 생성, 컨테이너가 비root(uid 999 `setty`)로 실행됨
- MySQL healthcheck의 `-p"$$MYSQL_ROOT_PASSWORD"` 이스케이프 동작 → 약 15초 만에 `healthy`
- compose 프로젝트명 고정(`name: setty`)으로 볼륨이 `setty_db-data`로 생성됨
- 앱이 `SPRING_DATASOURCE_URL` 오버라이드로 `db:3306`에 접속, JPA가 테이블 4개 생성
- `/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`
- 앱 컨테이너 자체 healthcheck 통과 → 이미지 안의 `curl` 정상
- `scripts/validate.sh` 실행 → `헬스체크 통과`
- **재배포 시 DB 유지**: 마커 행을 넣고 `start.sh`와 같은 순서로 2차 배포 →
  MySQL 컨테이너 ID가 그대로였고 마커 행이 살아남음

**아직 검증하지 않음**

- CodeBuild·CodeDeploy·CodePipeline 실제 실행
- `/opt/setty/.env` 절대경로 존재 여부 (로컬에서는 임시 경로로 대체해 검증했다)
- 컨테이너 안에서의 S3 업로드 — metadata hop limit에 걸리면 이것만 실패한다
- 호스트 nginx → `127.0.0.1:8080` 연결

## 6. 알려진 제약

**서버 테스트가 배포 게이트에 없다.** `server`의 `@SpringBootTest`들은 실행 중인 MySQL(`localhost:3306`)을 요구해서,
DB가 없으면 Hibernate가 Dialect를 정하지 못하고 컨텍스트 로딩부터 실패한다. CodeBuild 컨테이너에는 MySQL이 없으므로
`buildspec.yml`은 테스트를 돌리지 않는다. 테스트를 게이트로 만들려면 먼저 테스트가 DB 없이 돌 수 있어야 한다.
(`.github/workflows/ci.yml`도 현재 client만 검사한다.)

**롤백 수단이 없다.** 이미지 태그가 `setty-app:latest` 하나뿐이라 이전 이미지로 되돌릴 수 없다.
되돌리려면 CodeDeploy에서 이전 리비전을 재배포한다.

**다운타임이 있다.** 앱 컨테이너를 강제 재생성하므로 교체 중 요청이 실패한다. DEC-026에서 허용한 범위다.

## 7. 배포가 실패하면

CodeDeploy 콘솔에서 실패한 훅의 로그를 먼저 본다. EC2에서는 다음을 본다.

```sh
# 배포 훅 로그
sudo tail -n 200 /var/log/aws/codedeploy-agent/codedeploy-agent.log
sudo ls /opt/codedeploy-agent/deployment-root/

# 앱 상태
docker ps -a
docker logs --tail 200 setty-app
docker compose -f /opt/setty/app/docker-compose.prod.yml ps
```

MySQL 컨테이너를 되살릴 때 `docker compose down -v`를 쓰지 말 것. `-v`가 `db-data` 볼륨을 지운다.

### 배포는 성공했는데 502가 난다면

앱 컨테이너 포트를 `127.0.0.1:8080:8080`으로 묶어 두었다. 호스트에 설치된 nginx를 전제한 설정이다.
nginx가 실제로는 컨테이너라면 루프백에 닿지 못해 502가 난다. `validate.sh`는 호스트에서 도는 탓에 이 경우에도 통과한다.

```sh
# nginx가 컨테이너인지 확인
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}'

# 호스트에서는 응답하는지 확인
curl -i http://127.0.0.1:8080/actuator/health
```

컨테이너였다면 `docker-compose.prod.yml`의 `ports`를 `"8080:8080"`으로 되돌린다.
