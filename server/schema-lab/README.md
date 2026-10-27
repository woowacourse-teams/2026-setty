# 점진적 스키마 배포·롤백 로컬 실험

공통 `schema-lab` 프로필과 격리 Docker Compose를 사용한다. 앱은 Java 21 컨테이너에서 각각 실행하고, 같은 실험 DB를 공유한다. C0 준비부터 시작해 단계별로 진행한다.

| 항목 | 값 |
| --- | --- |
| DB | `setty_schema_transition_lab`, 호스트 `127.0.0.1:13306` |
| Blue·Green | 호스트 `127.0.0.1:18080`, `127.0.0.1:18081`. 각 컨테이너 내부는 8080 |
| 요청 진입점 | `http://127.0.0.1:18000` |
| 실행·원자료 | 저장소의 `.local/schema-lab/<run-id>/`. Git 커밋 대상에서 제외 |
| DB 수명 | 같은 run ID에서는 배포·롤백·중지 후에도 동일 볼륨 유지 |

Compose의 계정·암호는 이 루프백 실험 환경의 고정 fixture다. 실제 dev·prod 접속 정보를 사용하지 않는다. 이미지 대역은 PNG fixture만 지원하고 실제 파일·S3 객체를 저장하지 않는다. 이미지 URL 렌더링은 검증 대상이 아니다.

Docker에는 `/tmp/setty-schema-<run-id>-<임의값>/`의 실행용 복사본만 마운트한다. macOS의 `Documents` 접근 권한 없이 실행하기 위한 경로이며 `state.json`의 `runtime_root`에서 확인한다. 원본 JAR·데이터 대장·검증 기록은 `.local`에 보관한다. 임시 디렉터리가 사라졌다면 `init`, `start`, `route` 순서로 복원한다.

## 1. C0 기준 환경 준비

Docker Desktop 또는 Docker Engine·Compose, Python 3, 빌드용 Java 21이 필요하다. macOS에서는 설치된 Java 21을 자동 선택하며, 다른 환경은 `JAVA_HOME` 또는 `SCHEMA_LAB_JAVA_HOME`을 설정한다.

저장소 루트에서 실행한다.

```bash
lab() { python3 server/schema-lab/lab.py --run-id schema-20261027-01 "$@"; }

lab init
lab build C0
lab start blue C0
lab route blue
lab seed
lab verify --db-version 10
lab status
lab logs
```

`build`는 커밋된 앱 소스에서 JAR를 만들며, 이미 보관한 같은 버전은 덮어쓰지 않는다. 기존 Gradle 캐시만 사용할 때는 `build C0 --offline`처럼 지정한다. `C0`에는 V1~V10만 있어야 한다. `C1/C2`는 V11, `C3`는 V12, `C4`는 V13까지인지 검사한다. 뒤 단계 SQL이 먼저 실행되는 것을 막기 위한 검사다.

`seed`는 활성 C0·V10·빈 매물 테이블에서만 실행한다. 합성 판매자와 E1·E2를 준비하고 실제 등록 API 201, 수정 API 204, 목록·상세·DB 값의 일치를 검사한다. 실패 시 자동 삭제·초기화·재등록하지 않는다. 요청 원자료와 DB 상태를 확인한 뒤 이어서 진행한다.

이 단계 완료 상태는 Blue C0, DB V10, E1·E2 2행이다. 이때 멈추고 다음 단계의 C1 코드·마이그레이션을 별도로 구현한다.

## 2. 이후 단계의 공통 조작

각 단계 앱 코드를 커밋한 다음 해당 버전을 빌드한다. 아래는 C1이 실제 구현된 뒤의 예시다.

```bash
lab build C1
lab start green C1
lab verify --db-version 11 --slot green
lab route green
lab stop blue
```

`start`는 실행 중인 슬롯을 덮어쓰지 않고, `stop`은 활성 슬롯을 중지하지 않는다. `route`는 상대 슬롯을 중지하거나 DB를 되돌리지 않는다. 따라서 롤백할 버전의 슬롯을 유지한 상태에서 경로만 돌릴 수 있다.

```bash
lab route green
lab verify --db-version 11 --dual
```

이 롤백 예시는 Green C1·Blue C2가 모두 실행 중인 실험 구간에서 사용한다. 명령 출력의 실제 버전을 확인한다. Nginx 설정 검증 후 실제 응답의 upstream IP를 대상 컨테이너 IP와 비교해 경로 전환을 확인한다.

## 3. API·DB 검사

매물 라벨과 실제 ID·최신 제목은 `data.json`에서 관리한다. 요청마다 별도 JSON 증거 파일을 남기며 Authorization 값은 저장하지 않는다.

```bash
lab create N1 lab-N1-before-rollback
lab update E1 lab-E1-before-rollback
lab update N1 lab-N1-during-rollback
lab create N2 lab-N2-during-rollback
lab verify --db-version 11 --dual
```

각 명령은 실험 순서의 해당 시점에만 실행한다. 구 슬롯에 직접 요청할 때는 `--slot blue` 또는 `--slot green`을 붙인다. 같은 생성 라벨을 재사용하면 중복 등록을 거부한다.

```bash
lab sql <<'SQL'
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, title FROM listings ORDER BY id;
SQL
```

`sql`은 Compose 내부의 실험 DB 계정으로만 실행한다. 컬럼 확장 이후에는 해당 단계에 맞는 확인 SQL과 백필을 사용한다. `verify --dual`은 두 컬럼이 같은 값을 유지해야 하는 C1 백필 완료~C2 재전환 구간에서만 사용한다. C3 이후에는 기존 값이 NULL이거나 오래돼도 되므로 `--dual`을 사용하지 않는다.

## 4. 기록과 일시 중지

- `jars/<버전>.json`: 소스 커밋, JAR SHA-256, 마이그레이션 목록.
- `state.json`: 실행 ID, 슬롯별 산출물, 현재 활성 슬롯.
- `data.json`: API가 생성한 실제 ID와 기대 제목.
- `evidence/*.json`: 요청 라벨, 상태, 서버 요청 ID, upstream, 본문, 데이터 검증 결과.
- `evidence/containers-*.log`: MySQL·앱·Nginx 로그.

앱은 외부 `X-Request-Id`를 신뢰하지 않고 서버 UUID를 생성한다. Nginx 로그의 `probe_id`는 실험 라벨이고 `request_id`는 앱 응답의 서버 ID다. 둘을 구분해 요청 원자료와 로그를 연결한다.

```bash
lab logs
lab halt
```

`halt`는 이 실험 프로젝트의 컨테이너만 중지한다. 볼륨·JAR·원자료를 삭제하지 않는다. 새로운 회차는 다른 run ID로 분리하되, 기존 회차가 고정 호스트 포트를 쓰고 있다면 먼저 중지한다.

## 5. 도구 검증

```bash
python3 -m unittest discover -s server/schema-lab/tests -v
```

이 검사는 경로·산출물·활성 슬롯 보호와 요청 실패 기록 등을 확인하며, 실제 스키마 전환·롤백 실험을 대체하지 않는다.
