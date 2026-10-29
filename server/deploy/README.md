# 단일 EC2의 Nginx·슬롯 서비스를 사용하는 CodeDeploy 배포

환경별 EC2 한 대에서 Blue 8080과 Green 8081을 교대로 실행한다. 요청 경로는 `ALB → EC2:80 → Nginx → 활성 슬롯`이며 MySQL은 기존 EC2에 유지한다. 루트 [appspec.yml](../../appspec.yml)을 dev·prod가 공통으로 사용하고, 환경별 값은 EC2의 `/opt/setty/deploy.env`에서 지정한다.

## 최초 적용 전 서버와 AWS 설정

배포 훅은 이미 준비된 Nginx·슬롯 서비스를 사용한다. Nginx 전체 설정, systemd 유닛, 공통·슬롯 환경 파일과 JVM 옵션을 자동으로 덮어쓰지 않는다.

| 대상 | 필요한 상태 |
| --- | --- |
| CodeDeploy 애플리케이션 | 기존 환경별 애플리케이션 사용. dev 확인값은 `setty-backend` |
| 배포 그룹 | 해당 환경 EC2 대상, `IN_PLACE`·`WITHOUT_TRAFFIC_CONTROL` |
| CodePipeline | 기존 Source → Commands Build → CodeDeploy와 `QUEUED` 유지 |
| 빌드 아티팩트 | 루트 `app.jar`, 루트 `appspec.yml`, `server/deploy/**/*` 포함 |
| 자동 롤백 | 최초 전환 동안 CodePipeline Deploy 단계와 CodeDeploy의 이전 리비전 자동 재배포를 보류 |
| 레거시 서비스 | `setty-backend.service` 중지·자동 시작 해제 완료 |
| Nginx | 실행 중·자동 시작 등록. 80번에서 요청을 받고 활성 upstream 파일을 include. 접근 로그에 아래의 `deploy_probe` 항목 추가 |
| 슬롯 서비스 | `setty-backend@blue.service`, `setty-backend@green.service`가 등록되어 있음 |
| 활성 슬롯 | 실행 중·헬스체크 정상·자동 시작 등록 |
| 비활성 슬롯 | 중지·자동 시작 해제. 해당 포트에 다른 리스너가 없어야 함 |

CodeDeploy가 EC2를 로드밸런서에서 자동으로 제외하면 두 슬롯이 함께 영향을 받는다. 트래픽 전환은 이 훅의 Nginx reload가 담당하며 ALB 규칙이나 대상 그룹은 변경하지 않는다. 추가 ALB 조작 권한도 요구하지 않는다.

첫 전환의 `ApplicationStop`은 이전 성공 리비전의 스크립트를 실행한다. 새 AppSpec에서 제거해도 예전 중지 훅은 한 번 실행될 수 있다. 최초 슬롯 배포 전에는 요청 경로를 슬롯 서비스로 옮기고 레거시를 중지·자동 시작 해제해야 한다. 이전 성공 리비전으로 자동 재배포하면 레거시가 다시 활성화될 수 있으므로, 슬롯 방식의 첫 성공 배포를 기준점으로 만든 뒤 복구 정책을 검증한다. [AWS 훅 규칙](https://docs.aws.amazon.com/codedeploy/latest/userguide/reference-appspec-file-structure-hooks.html), [CodeDeploy 롤백](https://docs.aws.amazon.com/codedeploy/latest/userguide/deployments-rollback-and-redeploy.html)

## 서버 파일과 환경별 값

| 경로 | 역할 |
| --- | --- |
| `/opt/setty/setty.env` | 기존 공통 애플리케이션 설정·DB 접속 정보 |
| `/opt/setty/slots/blue/app.jar` | Blue JAR |
| `/opt/setty/slots/green/app.jar` | Green JAR |
| `/opt/setty/slots/blue/slot.env` | Blue 프로필·8080 포트·JVM 설정 |
| `/opt/setty/slots/green/slot.env` | Green 프로필·8081 포트·JVM 설정 |
| `/etc/systemd/system/setty-backend@.service` | 슬롯별 유닛. [저장소 템플릿](setty-backend@.service) 참조 |
| `/etc/nginx/setty/active-upstream.conf` | 활성 슬롯을 가리키는 upstream |
| `/var/log/nginx/setty-access.log` | 요청 식별자와 실제 전달 포트 검증 |
| `/opt/setty/deploy.env` | 배포 전용 설정. Bash 형식, root 소유·0600 |
| `/opt/setty/deployments/` | 배포 ID별 상태·기존 upstream 백업·전환 잠금 |

Blue·Green의 `slot.env`는 같은 환경의 `SPRING_PROFILES_ACTIVE`와 각 슬롯의 `SERVER_PORT`를 명시해야 한다. systemd는 공통 파일 다음에 슬롯 파일을 읽어야 한다. 기존 슬롯의 `JAVA_TOOL_OPTIONS` 등은 그대로 유지한다.

서버에 [배포 설정 예시](blue-green/deploy.env.example)를 준비한 뒤 환경에 맞춰 `/opt/setty/deploy.env`를 작성한다. 애플리케이션의 비밀 값은 이 파일로 옮기지 않는다.

```bash
sudoedit /opt/setty/deploy.env
sudo chown root:root /opt/setty/deploy.env
sudo chmod 0600 /opt/setty/deploy.env
```

dev는 `SETTY_DEPLOY_ENVIRONMENT=dev`, `SETTY_SMOKE_URL=https://www.setty.cloud/dev/api/listings?size=1`을 사용한다. prod 적용 전에는 슬롯의 프로필을 `prod`로 준비하고, 배포 설정도 다음처럼 지정한다.

```bash
SETTY_DEPLOY_ENVIRONMENT=prod
SETTY_SMOKE_URL=https://www.setty.cloud/api/listings?size=1
```

`SETTY_SMOKE_URL`은 이 EC2의 Nginx를 거치는 읽기 전용 HTTP API여야 하며 추가 쿼리 매개변수를 허용해야 한다. 훅이 요청 식별자를 붙이고 접근 로그에서 같은 식별자와 신규 포트를 확인한다. HTTP 200만으로 전환 성공을 판정하지 않는다. 캐시·다른 서버·iptables 우회 경로로 응답해 해당 로그가 없으면 검증에 실패한다.

관리하는 upstream 조각은 정규 파일이며 주석을 제외한 `server`가 정확히 하나여야 한다.

```nginx
upstream setty_backend {
    server 127.0.0.1:8080;
}
```

Nginx 접근 로그에는 `upstream=$upstream_addr`와 `deploy_probe=$arg_setty_deploy_probe`가 기록되어야 한다. 기존 dev 작업 기록의 로그는 `"GET /api/listings"`처럼 쿼리가 없는 형식이므로, **첫 자동 배포 전에 `deploy_probe` 항목을 추가해야 한다.** `http` 블록에서 해당 접근 로그가 사용하는 `log_format`을 아래처럼 수정한다. `setty`는 예시 이름이므로 기존 이름을 유지한다. 전체 쿼리를 기록할 필요는 없다.

```nginx
log_format setty '$remote_addr [$time_local] "$request_method $uri" status=$status '
                 'upstream=$upstream_addr deploy_probe=$arg_setty_deploy_probe '
                 'request_time=$request_time '
                 'upstream_time=$upstream_response_time';
access_log /var/log/nginx/setty-access.log setty;
```

설정 변경 후 구문 검증과 reload를 수행한다. 확인 요청의 로그에 `deploy_probe=manual-check`와 현재 활성 포트가 함께 있어야 한다. 누락된 경우 훅은 JAR 교체 전에 중단한다.

```bash
sudo nginx -t && sudo systemctl reload nginx.service
curl -fsS 'http://127.0.0.1/actuator/health?setty_deploy_probe=manual-check'
sudo tail -n 10 /var/log/nginx/setty-access.log
```

## 배포 흐름과 복구

`BeforeInstall`은 서버 전제만 확인한다. 슬롯 변경 전체는 `ValidateService`의 단일 컨트롤러에서 수행해 훅 사이에 잠금이나 실패 복구 문맥이 끊기지 않도록 한다. CodeDeploy는 배포별 archive를 자동으로 제공하므로 별도 `files` 설치 경로를 두지 않는다. 컨트롤러가 자신의 archive에 있는 JAR을 슬롯으로 복사하며, 이후 CodeDeploy의 파일 정리로 활성 슬롯 JAR이 삭제되지 않는다. [AWS AppSpec 파일 규칙](https://docs.aws.amazon.com/codedeploy/latest/userguide/reference-appspec-file-structure-files.html)

1. 배포 잠금을 획득하고, 미완료 배포 표시가 없는지 확인한다.
2. Nginx의 설정과 실제 로컬 전달 포트, 활성 슬롯 헬스, 외부 API 경로를 확인한다.
3. 배포 ID·이전/신규 슬롯·JAR SHA-256·기존 upstream 백업을 저장한다.
4. 비활성 슬롯 JAR만 원자적으로 교체하고 해당 서비스를 기동한다. 이전 JAR과 환경 파일은 보존한다.
5. 신규 슬롯의 헬스체크를 기다리고 자동 시작을 등록한다.
6. upstream 파일을 같은 디렉터리에서 원자적으로 교체하고 `nginx -t`·reload를 수행한다.
7. 로컬·외부 요청의 접근 로그에서 신규 포트 전달을 확인한 뒤 관찰한다.
8. 이전 슬롯의 established 연결이 두 차례 연속 0개인지 확인한다. 활성 경로를 재확인하고 이전 슬롯을 중지·자동 시작 해제한다.
9. 신규 응답을 재확인하고 완료 상태를 기록한다. 정상 완료 시 JVM은 하나만 남는다.

실패하면 이전 슬롯의 헬스와 자동 시작을 확보하고 기존 upstream을 복원·검증한다. 신규 슬롯의 연결이 비어야 해당 JVM을 종료하며, 복귀가 성공해도 CodeDeploy에는 실패를 반환한다. 이전 JVM 종료 후 발생한 오류는 보관한 JAR로 이전 JVM을 다시 기동한 뒤 복구하므로 재기동 시간이 필요하다. DB 변경은 되돌리지 않는다.

복구 자체가 실패하거나 연결이 끝나지 않으면 프로세스를 강제 종료하지 않고 `recovery_required`와 `pending` 표시를 남긴다. 이후 자동 배포는 차단되며 운영자가 경로와 연결을 확인해야 한다. 프로세스 강제 종료·호스트 재부팅·CodeDeploy 훅 시간 초과처럼 셸의 복구 처리가 실행되지 못한 경우도 `pending` 표시로 다음 배포를 막는다. 서버 외부 장애까지 자동 복구를 보장하는 구성은 아니다.

기존 `server/deploy/scripts/`와 단일 서비스 파일은 이전 방식의 참조로 보존했으며 새 AppSpec은 호출하지 않는다. 이벤트·스케줄러의 동시 실행 제어나 DB 스키마 호환성은 이 훅에서 변경하지 않는다. prod 적용 전에는 두 버전의 DB 호환성과 백그라운드 중복 처리 안전성을 별도로 확인한다.

애플리케이션은 오래 남은 이벤트 발행 기록을 주기적으로 감지해 재발행한다. 감시 기준, 환경변수와 결제 만료 경합의 적용 한계는 [이벤트 복구 문서](../docs/event/event-recovery.md)를 참조한다.

## 대기 시간과 상태 확인

| 설정 | 기본값 |
| --- | ---: |
| 신규·복구 JVM 헬스 대기 | 180초 |
| reload 후 실제 경로 확인 | 30초 |
| 전환 후 관찰 | 60초 |
| 각 슬롯 연결 종료 대기 | 180초 |
| 폴링 간격 | 2초 |

관찰 60초는 기본값이며 최종 운영 정책을 확정한 값이 아니다. `/opt/setty/deploy.env`에서 조정한다. 컨트롤러는 정상 흐름과 복구 대기의 합이 AppSpec의 1,200초 예산에 들어가는지 확인하고, 과도한 설정은 변경 전에 거부한다.

배포 로그는 CodeDeploy 훅 출력에서 확인한다. 상태와 미완료 배포는 다음으로 조회한다. `<배포 ID>`에는 CodeDeploy의 실제 `d-...` 값을 넣는다.

```bash
sudo cat /opt/setty/deployments/pending
sudo cat /opt/setty/deployments/<배포 ID>/state
sudo systemctl status setty-backend@blue.service setty-backend@green.service nginx.service
sudo tail -n 30 /var/log/nginx/setty-access.log
```

`pending`이 있으면 먼저 상태의 `old_slot`·`new_slot`, 백업된 `upstream.before.conf`, 실제 요청 포트를 대조한다. 유지할 슬롯의 기동·헬스·Nginx 설정·외부 전달을 확인하고 다른 슬롯의 연결과 자동 시작을 정리한 뒤 표시를 해제한다. 상태를 확인하지 않고 `pending` 파일만 삭제해 재시도하지 않는다. 같은 배포 ID가 완료됐고 JAR·활성 경로가 그대로라면 훅 재실행은 추가 기동 없이 종료한다.

## 로컬·CI 검증

```bash
find server/deploy -name '*.sh' -print0 | xargs -0 -n 1 bash -n
python3 -m unittest discover -s server/deploy/tests -v
```

테스트는 실제 셸 훅을 실행하고 systemd·Nginx·HTTP·소켓 명령만 임시 호스트로 대체한다. 양방향 전환, prod 설정, 잘못된 프로필·서비스 경로, 기동·헬스·reload·외부 검증 실패, 관찰 중 오류, 연결 종료 시간 초과, 복구 실패와 미완료 배포 차단을 검증한다. 실제 EC2/Nginx에서의 리허설은 별도다.
