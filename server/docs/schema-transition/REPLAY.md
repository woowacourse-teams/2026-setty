# 점진적 스키마 배포·수동 롤백 재현 순서

[실행 결과와 적용 한계](README.md)를 먼저 확인한다. 실험용 코드는 원복했으므로 **별도 clone 또는 독립된 작업 폴더에서 보관한 커밋을 순서대로 체크아웃**한다. 모든 단계에서 같은 run ID·DB 볼륨을 유지한다. 기존 실험을 초기화하지 않는다.

Docker Desktop/Engine·Compose, Python 3, Java 21이 필요하다. 호스트 13306·18000·18080·18081 포트가 비어 있어야 한다. 아래 명령은 저장소 루트의 Bash 터미널 기준이다. 새로운 회차는 새로운 run ID를 사용한다.

## 1. C0 기준 환경

```bash
git switch --detach 2df09c6b54970bebe0178aad6ba211d30d73e923
export LAB_RUN_ID=schema-replay-01
export LAB_ROOT="$PWD/.local/schema-lab/$LAB_RUN_ID"
lab() { python3 server/schema-lab/lab.py --run-id "$LAB_RUN_ID" "$@"; }

lab init
lab build C0
lab start blue C0
lab route blue
lab seed
lab verify --db-version 10
lab logs
```

앱 소스는 원회차 C0의 `646f0c3`과 같고 실행 도구가 함께 있는 커밋이다. 이 커밋 차이는 `git diff 646f0c3 2df09c6 -- server/src/main`으로 확인할 수 있다. 생성 ID는 예시 숫자로 가정하지 않고 `data.json`의 E1·E2를 사용한다.

`build`는 같은 버전의 JAR를 덮어쓰지 않는다. 로컬 Gradle 캐시만 사용하려면 `build C0 --offline`처럼 지정한다. 이후 버전에도 같은 옵션을 붙일 수 있다.

## 2. C1 확장과 공존

```bash
git switch --detach 40aee0843108642bf725789d7284f400d2c44676
lab build C1
lab phase c0-baseline
lab checkpoint c0-baseline
```

별도 터미널을 같은 저장소 루트에서 열고, 위에서 선택한 run ID로 연속 관찰을 시작한다.

```bash
python3 server/schema-lab/observe.py .local/schema-lab/schema-replay-01
```

제어 터미널에서 이어서 실행한다.

```bash
lab phase c1-expansion
lab start green C1
lab verify --db-version 11 --slot blue
lab verify --db-version 11 --slot green
lab update E1 lab-E1-by-C1 --slot green
lab update E1 lab-E1-by-C0 --slot blue
lab verify --db-version 11 --slot blue
lab verify --db-version 11 --slot green
lab checkpoint c1-coexistence
lab logs
```

체크포인트의 E1은 `title=lab-E1-by-C0`, `display_title=lab-E1-by-C1`이어야 한다. E2의 신규 값은 NULL이다. C0·C1 모두 기존 컬럼을 읽으므로 최신 E1을 반환한다.

```bash
lab route green
lab stop blue
lab logs
```

## 3. C0 종료 후 백필

C0가 중지된 것을 `lab status`로 확인하고 실행한다.

```bash
lab phase backfill
lab sql <<'SQL'
SELECT DATABASE();
UPDATE listings SET display_title=title
WHERE NOT (CAST(display_title AS BINARY) <=> CAST(title AS BINARY));
SELECT ROW_COUNT();
SQL
lab verify --db-version 11 --dual
```

위 SQL을 다시 실행한다. 첫 실행은 2행, 재실행은 0행 변경이어야 한다. NULL만 채우는 갱신은 E1의 오래된 신규 값을 고치지 못한다.

```bash
lab checkpoint backfill-complete
lab logs
```

실제 대량 데이터에서는 작은 ID 범위로 나누고 처리 위치·잠금·지연을 별도로 관리한다. 이 순서는 매물 2행의 기능 실험이다.

## 4. C2 읽기 전환

```bash
git switch --detach 0e215d0e1e66833dcf5308d8cbd9edd7cc8207de
lab build C2
lab phase c2-deploy
lab start blue C2
lab verify --db-version 11 --dual --slot blue
lab verify --db-version 11 --dual --slot green
lab route blue
lab phase c2-active
lab create N1 lab-N1-before-rollback
lab update E1 lab-E1-before-rollback
lab verify --db-version 11 --dual
lab checkpoint before-rollback
lab logs
```

3행이며, C1 Green은 그대로 유지한다. C2 JAR에는 V11까지만 포함한다.

## 5. C1 수동 롤백과 C2 재전환

정상 C2에서 의도적으로 경로만 바꾼다. 새 빌드·DB 복원·역방향 마이그레이션은 없다.

```bash
lab phase rollback-to-c1
lab route green
lab verify --db-version 11 --dual
lab checkpoint rollback-c1
lab phase c1-rollback-writes
lab update N1 lab-N1-during-rollback
lab create N2 lab-N2-during-rollback
lab verify --db-version 11 --dual
lab checkpoint rollback-writes
lab phase return-to-c2
lab route blue
lab verify --db-version 11 --dual
lab checkpoint c2-return
lab logs
lab stop green
```

DB V11과 N1의 동일 ID를 유지하고, 재전환 후 4행·최신 제목을 확인한다. 이 지점에서 C1을 유지하는 롤백 기간을 종료한다. 기존 컬럼만 쓰는 C0는 이번 롤백 대상이 아니다.

## 6. C3 기존 참조·제약 제거

```bash
git switch --detach 3a09734779db022006836f38320f5eff8c97ca27
lab build C3
lab phase c3-deploy
lab start green C3
lab verify --db-version 12 --slot blue
lab verify --db-version 12 --slot green
lab route green
lab phase c3-active
lab create N3 lab-N3-after-legacy-stop
lab checkpoint c3-new-row-legacy-null
lab verify --db-version 12 --slot blue
lab update N3 lab-N3-by-C2 --slot blue
lab update N3 lab-N3-final
lab verify --db-version 12 --slot blue
lab verify --db-version 12 --slot green
lab checkpoint c3-complete
lab logs
lab stop blue
```

N3 생성 시 기존 값은 NULL이고, C2가 이 행을 읽고 수정할 수 있어야 한다. 마지막 C3 수정 후 기존 값은 오래돼도 된다. **이제 `--dual`을 쓰지 않는다.** 신규 값과 ID 대장을 기준으로 판정한다. 기존 컬럼을 읽는 C1 복귀도 종료한다.

## 7. C4 컬럼 삭제와 C3 재기동

```bash
git switch --detach 14a36bf9173f39ef128504067fe18e98e0358c01
lab build C4
lab sql <<'SQL'
SELECT COUNT(*) AS missing_new_title FROM listings WHERE display_title IS NULL;
SQL
```

누락 0과 C0~C2 종료를 확인한다. 오류가 있으면 후속 DDL을 진행하지 않는다.

```bash
lab phase c4-deploy
lab start blue C4
lab verify --db-version 13 --slot green
lab route blue
lab phase c4-active
lab create N4 lab-N4-final
lab verify --db-version 13
lab checkpoint c4-final-data
lab logs
lab phase c3-restart-on-v13
lab stop green
lab start green C3
lab verify --db-version 13 --slot green
lab update E2 lab-E2-initial --slot green
lab verify --db-version 13
lab checkpoint c3-restart-v13
lab logs
lab stop green
lab phase final-verification
lab verify --db-version 13
lab checkpoint final
```

기존 컬럼이 없고 신규 컬럼은 NOT NULL이어야 한다. C4가 서비스하는 동안 C3도 V13에서 검증 설정을 그대로 둔 채 재기동·조회·수정돼야 한다. 마지막 6건의 ID와 제목을 [원회차 결과](README.md)의 라벨별 최종 값과 대조한다. 새 회차의 숫자 ID 자체는 원회차와 같다고 가정하지 않는다.

## 8. 관찰 종료와 보존

```bash
touch "$LAB_ROOT/observer.stop"
lab logs
lab halt
```

관찰기 종료 후 JSONL의 HTTP 상태·유효 목록·단계별 표본 수를 집계한다. 0.5초 간격의 표본 결과를 모든 순간의 무중단 보장으로 확대하지 않는다.

원본 JAR·체크포인트·요청·관찰·로그와 DB 볼륨을 보존한다. 이후 원복된 코드가 있는 브랜치로 돌아가더라도 실험 DB는 V13 그대로다. 이 DB에 C0~C2를 다시 연결하지 않는다. 운영 환경 리허설은 별도 변경 계획으로 진행한다.
