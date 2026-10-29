# 두 JVM 이벤트 중단 복구의 로컬 검증

실제 애플리케이션 JAR 두 개, 새 MySQL 8.4.11 컨테이너와 로컬 모의 결제 서버로 [이벤트 복구](../docs/event/event-recovery.md)를 확인한다. 테스트 전용 엔드포인트나 지연 코드를 제품에 넣지 않고 DB 잠금으로 실제 이벤트 리스너를 대기시킨다.

## 실행 조건과 범위

- Java 21, Python 3.11 이상과 실행 중인 Docker가 필요하다.
- 저장소 루트에서 실행한다. `--output`은 존재하지 않는 새 폴더여야 한다.
- 앱, 모의 결제 서버와 MySQL은 루프백 주소의 임시 포트를 사용한다. MySQL 컨테이너와 데이터는 매번 새로 만든다.
- 모의 결제만 승인하며 푸시는 `noop`이다. 실제 결제와 외부 푸시를 호출하지 않는다.
- 검증이 만든 JVM만 종료하고, 종료 시 검증용 컨테이너와 익명 볼륨을 제거한다. JAR 해시, 프로세스 ID, 상태와 로그는 출력 폴더에 보존한다.

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./server/gradlew -p server bootJar
python3 server/event-recovery-lab/verify.py \
  --jar server/build/libs/server-0.0.1-SNAPSHOT.jar \
  --java "$(/usr/libexec/java_home -v 21)/bin/java" \
  --output .local/event-recovery/new-run-id
```

기존 버전과의 공존을 확인하려면 해당 공용 기준 커밋의 소스를 별도 폴더에 추출해 빌드하고 `--blue-jar /absolute/path/to/baseline.jar`를 추가한다. 현재 브랜치로 기존 작업 커밋을 가져올 필요는 없다. `--jar`는 Green, `--blue-jar`는 Blue가 사용하며 생략하면 같은 JAR로 두 JVM을 실행한다.

## 실행하는 다섯 가지 시나리오

| 시나리오 | 자극 | 판정 기준 |
| --- | --- | --- |
| 결제 이벤트 처리 중 강제 종료 | 주문 행을 잠근 뒤 Blue에 결제 확인 요청, `PaymentCompleted`의 `PROCESSING`을 확인하고 Blue에 SIGKILL | Green의 PID가 그대로인 상태에서 결제 DONE, 주문 CONFIRMED, 배송 1건, 판매자 정산 1건으로 수렴 |
| 배송 등록 처리 중 강제 종료 | 배송 판정 행의 삽입을 잠근 뒤 `OrderConfirmed` 처리 중 Blue에 SIGKILL | 이미 확정된 주문에서 배송이 복구되고 정산이 중복되지 않음 |
| 서로 다른 JVM의 리스너 중첩 | Blue의 원본 처리를 잠금으로 멈추고 Blue의 재발행 주기를 24시간으로 설정해 Green만 재시도 | 원본 Blue와 재처리 Green이 살아 있는 상태에서 같은 발행의 시도 수가 2 이상, 잠금 해제 후 업무 결과는 각 1건 |
| 같은 이벤트 재전달 | 실제 발행에서 캡처한 결제와 주문 확정 이벤트를 각각 두 개씩 새 발행 기록으로 추가 | 주문, 결제, 배송과 판매자 정산의 건수가 증가하지 않고 미완료 발행 기록은 없어짐 |
| 재시도 소진 | 유효한 이벤트를 시도 횟수 5인 FAILED 기록으로 추가 | 재발행 주기 1초인 환경에서 3초간 관찰해 FAILED와 시도 횟수 5를 유지 |

세 업무 주문의 검증 데이터는 모두 가상 데이터다. 주문 만료 스캔은 24시간으로 늦추고 만료시각을 10분 뒤로 설정해, 복구 자체와 만료 정책의 경합을 분리한다.

## 결과와 적용 한계

성공 시 `result.json`의 `status`는 `passed`, `container_removed`는 `true`다. 오류가 나면 실패 지점과 JVM 및 MySQL 로그를 함께 확인한다. 검증 폴더를 재사용하거나 기존 결과를 덮어쓰지 않는다.

감시 기준은 15초, 검사 및 재발행 주기는 각각 1초로 가속한다. 기존 기준 JAR에 감시 설정이 없다면 Blue는 감시를 새로 활성화하지 않고 Green이 감시를 담당한다. 시작 시 전체 재발행은 두 JVM 모두 끈다. **가속 시험에서 측정한 복구시간을 운영 기본값인 감시 5분, 검사 30초, 재발행 1분의 복구시간으로 사용하지 않는다.**

이 시험은 Nginx 전환, systemd 종료 유예, AWS 환경, 실제 PG 통신, 외부 푸시 중복과 주문 만료 경합을 검증하지 않는다. 인프라 전환은 [배포 스크립트 테스트](../deploy/tests/test_blue_green.py)와 별도 환경 검증으로 확인한다.

2026-10-29의 서버 테스트 집계와 동일 및 혼합 버전 회차 결과는 [검증 요약 JSON](results/2026-10-29.json)에 보존한다. 전체 로그는 각 실행의 출력 폴더에 남으며 Git에는 포함하지 않는다.
