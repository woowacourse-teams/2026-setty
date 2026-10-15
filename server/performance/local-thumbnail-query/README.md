# 대표 사진 쿼리 비교 재현

대표 사진을 한 건만 반환하던 MIN 서브쿼리와 `displayOrder = 1` 조회를 같은 입력으로 비교한다. [작업 기록](https://app.notion.com/p/3f62dbc1718780e88094f8946c69bb1d)의 핵심 로컬 비교와 최종 dev 관측을 검토할 수 있는 도구·선별 결과를 보존한다.

## 포함한 자료

- `src/test/java/.../ThumbnailQueryComparisonTest.java`: 실제 HTTP/MVC/service/JPA/MySQL 경로에서 두 JPQL을 교차 실행한다. 쿼리 선택만 test spy로 바꾸며 양쪽 모두 같은 EntityManager를 사용한다.
- `reproduce.py`: 당시 소스 커밋을 별도 폴더에 추출하고 회귀·실험·분석을 실행한다.
- `benchmark.gradle`: 명시적으로 실행할 때만 실험 소스를 포함한다. 일반 `./gradlew test`와 CI에는 추가되지 않는다.
- `analyze.py`: 새 실행의 JSONL·CSV·로그·본문 해시를 대조하고 통계를 계산한다.
- `verify-records.py`, `records/`: Git에 포함한 과거 측정 CSV와 집계·복원 증거를 오프라인에서 재검산한다.
- [사진 수 비교 절차](../listing-images.md), [사진 변경·복원 SQL](../fixtures/set-listing-images-5000.sql): 별도 환경에서 1→5→1개 조건을 준비하는 절차다.

요청량 1·3·5 RPS 탐색은 개선 대상 선택 이전의 조사이므로 이번 묶음에 포함하지 않는다. 임시 디버깅 로그·Gatling HTML·실패한 CI 로그·중복 실행 결과도 제외했다. MIN 시도에서 시간이 줄지 않았다는 판단 근거는 `records/min-attempt-20261011.json`에 정상 완료 회차 요약으로 남겼다. 배포 중 502로 중단된 회차는 통계에서 제외했으며, 해당 실험 전체가 완료됐다고 표시하지 않는다.

## 당시 코드로 로컬 실험 재현

Java 21, Docker가 필요하다. 저장소의 MySQL 8.4.11 Testcontainers 지원을 사용하고 테스트 DB `setty_test`에서만 합성 데이터를 만든다. 기존 DB·dev API·S3에 접속해 부하를 주는 실험이 아니다. Gradle 의존성과 Docker 이미지가 처음에는 다운로드될 수 있다.

현재 develop의 공개 목록은 JDBC 경로로 변경되어 이 JPA 비교의 대상이 아니다. **당시 개선 커밋 `9938ea86fa9de7c428caec4eb435f9d6ab184d24`에 소스를 고정**한다. `reproduce.py`는 Git에 보존된 해당 커밋의 `server/`를 새 출력 폴더에 추출하고 회귀 테스트 → 비교 실험 → 원본 대조 분석 순서로 실행한다. 현재 체크아웃·브랜치·다른 워크트리를 바꾸지 않는다.

`server/`에서 실행한다. 얕은 clone에 기준 커밋이 없다면 먼저 `git fetch origin 9938ea86fa9de7c428caec4eb435f9d6ab184d24`로 가져온다.

```bash
python3 performance/local-thumbnail-query/reproduce.py
# 또는 새 결과 폴더를 직접 지정한다. 기존 경로는 덮어쓰지 않는다.
python3 performance/local-thumbnail-query/reproduce.py --output build/reports/my-thumbnail-replay
```

기본 출력은 `build/reports/thumbnail-replay-*`이다. 추출 소스, 실행 로그, 소스 커밋, 도구 SHA-256, 요청별 측정과 `comparison.json`을 함께 남긴다. 측정 환경이 달라 과거와 동일한 시간이 나온다는 보장은 없다. 현재 develop의 성능·회귀 검증으로 해석하지 않는다. 일반 CI는 현재 코드를 검사하며 이 과거 실험을 자동 실행하지 않는다.

기존 실행의 원본을 다시 검증하려면 다음 명령을 사용한다.

```bash
python3 performance/local-thumbnail-query/analyze.py /absolute/path/to/thumbnail-replay-output
```

합성 매물은 5,000+20건이며, 앞의 5,000건만 사진 1개/5개로 바꾼다. 전체 사진 행은 5,020/25,020개이고 반환 대표 사진은 두 방식 모두 5,020개다. 조건별·방식별 사전 확인 1건, 워밍업 20건, 본 측정 100건×3회로 총 1,284건 중 본 측정 1,200건이다. 순차 HTTP 요청이며 응답 뒤 고정 대기는 없다. 쿼리별 선행 순서를 교차한다.

모든 요청의 HTTP 200, 요청 ID와 서버 계측의 1:1 연결, 응답·세 처리 구간 완료, 목록·사진·요약 수, 전체 응답 SHA-256 일치를 검증한다. 기존 MIN JPQL은 테스트에 보존했고 후보는 실제 repository의 `@Query`에서 읽는다. SQL과 `EXPLAIN FORMAT=JSON`·`EXPLAIN ANALYZE`도 출력한다. 실행 계획 측정 시간은 HTTP 시간과 구분한다.

`summary.json`의 완료 여부를 확인한 뒤 `analyze.py`가 생성하는 `comparison.json`을 읽는다. 측정 시간은 성능 회귀 assertion의 고정 임계값으로 사용하지 않는다. 테스트 성공은 입력·응답·증거의 일관성 확인이며 매번 동일한 개선율을 보장하지 않는다.

## 보존 결과 재검산

Docker·서버 없이 Python 3 표준 라이브러리만 사용한다. 이 명령은 저장된 파일을 수정하지 않는다.

```bash
python3 performance/local-thumbnail-query/verify-records.py
```

| 자료 | 검증할 수 있는 범위 | 한계 |
| --- | --- | --- |
| `records/local-20261011/` | 원래 CSV 1,284건의 ID·건수·본문 해시 일관성, 조건별 300건 집계와 회차별 통계, 감소율 | HTTP 본문·전체 로그는 Git에 포함하지 않아 원본 본문 해시와 로그를 다시 계산하는 검증은 불가 |
| `records/dev-20261012/` | 원래 CSV 840건의 클라이언트 통계, 서버 집계의 요청 ID 순서 해시, 실행 간격·데이터/배포 불변·복원 기록 | 서버 원본 행을 내보내지 못해 서버 중앙값·p95를 원본 행에서 재계산할 수 없음 |
| `records/min-attempt-20261011.json` | MIN 시도 당시 정상 회차 요약과 중단·제외 이유 | 전체 실행 원본을 보존한 자료가 아니며 성공한 전후 개선 결과가 아님 |

`records/source-manifest.json`은 그대로 복사한 파일의 원래 상대 경로와 SHA-256을 기록한다. dev 집계는 원래 `comparison.json`에 실행별 `client-summary.json`·`journal-summary.json`을 합친 것이며, `verification-evidence.json`에는 준비·복원 및 실행별 snapshot만 모았다. 결과 수치를 새로 측정하거나 추정해 채우지 않았다.

## 결과와 선택

2026-10-11 로컬: Mac ARM64, Java 21.0.12.1, MySQL 8.4.11, 워밍업 이후 조건·방식별 본 측정 300건을 합친 중앙값이다. 당시 기준 소스는 MIN 구현 `ca69eca99dc16632fcdd2f60174033b4c6f18d6f`에 순서 1 후보를 적용한 상태이며, 그 후보는 [PR #427](https://github.com/woowacourse-teams/2026-setty/pull/427)의 `9938ea86`으로 병합됐다. 재현 실행은 병합된 후보 커밋에 고정하며, 당시 실행 환경은 `metadata.json`에 보존했다. 현재 도구의 변경은 실행 편의와 오류 안내에 한정하고 비교 입력·JPQL·측정 순서는 유지한다.

| 실험 매물당 사진 | 사진 처리 MIN → 순서 1 | 전체 응답 수신 MIN → 순서 1 |
| --- | --- | --- |
| 1개 | 22.78 → 18.27ms | 58.96 → 54.94ms |
| 5개 | 52.43 → 19.99ms | 87.41 → 53.57ms |

사진 5개 조건의 중앙값 감소는 각각 61.9%, 38.7%였다. 사진 처리는 SQL 외에 엔티티 수신·대표 사진 Map 구성도 포함한다. 사진 5개 조건의 두 JSON 실행 계획도 보존했다. 반복 MIN 서브쿼리가 사라졌지만 바깥 사진 테이블 스캔은 남았으므로 모든 DB 스캔을 제거했다고 주장하지 않는다.

첫 MIN 시도에서는 반환 사진 수가 25,020→5,020개로 줄었지만 dev 사진 처리 중앙값은 약 130ms로 유지됐다. 따라서 반환량 감소만으로 개선을 확정하지 않고 반복 서브쿼리 제거로 방향을 바꿨다. 당시 배포 중 502로 멈춘 회차는 제외했고 후속 로컬 비교와 최종 dev 관측을 별도로 수행했다.

2026-10-12 최종 dev: 매물 5,138건(실험 5,000+다른 매물 138), 사진 1개 3회·5개 3회·복원 1회, 순차 사용자 1명·응답 뒤 1초 대기였다. 840건 모두 성공했고 본 측정은 700건이다. 사진 처리 중앙값은 1개 34.19~35.16ms, 5개 39.13~39.82ms, 복원 33.15ms였다. 추가 사진 메타데이터 20,000행을 제거하고 원래 사진 행 해시 일치를 확인했다.

과거 dev 약 130ms와 최종 약 40ms는 매물 수·배포·시간대가 달라 동일 입력 전후 감소율로 계산하지 않는다. 로컬·dev를 합산하지 않으며, 최대 처리량·실제 사용자 SLA·사진 파일 다운로드·브라우저 렌더링에 대한 증거로 확대하지 않는다.

## 도구 검증 — 2026-10-15

현재 develop `8b153acd`에서 관련 기존 회귀 테스트와 Gatling 컴파일을 확인했다. `reproduce.py`로 고정 커밋의 관련 회귀 테스트 17개, 비교 HTTP 1,284건, 원본 대조 분석을 통과했다. 보존 결과 검증은 로컬 1,284건·dev 840건을 통과했고, 로컬 중앙값 또는 dev 요청 ID 해시를 변조한 사본은 거부됐다. localhost Gatling에서는 5,138건 응답의 120회 정상 처리와 5,020건 응답의 ID 누락 시 첫 요청 뒤 중단을 확인했다.

이번 재실행은 도구가 동작하는지 확인하기 위한 검증이다. 새 실행 시간을 과거 성능 수치에 합산하거나 현재 develop의 개선율로 주장하지 않는다. 공유 dev 측정이나 데이터 변경은 수행하지 않았다.
