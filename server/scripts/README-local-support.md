# 로컬 웹에서 취소 지원 상담 실행하기

이 실행기는 **로컬 전용 Spring HTTP 서버 + 새 MySQL Testcontainer + 합성 회원/주문**을 시작한다. 기존 개발/운영 DB를 사용하지 않고, 기존 `.env`·AWS 프로필·Spring/JVM 환경을 이어받지 않는다. Docker가 켜져 있어야 하며 Java 21, Python 3가 필요하다. 클라이언트 개발 서버에는 프로젝트 의존성이 요구하는 Node 버전이 필요하다.

프로덕션 코드에는 테스트 회원이나 시드를 넣지 않는다. 실행기는 `src/test`와 별도 Gradle 작업에 있고 일반 `test`/`check` 또는 운영 jar 실행에 포함되지 않는다. 기존 MockMvc smoke와 달리 실제 HTTP 포트를 연다. **AI 키를 읽지 않는 비활성 모드에서 기동·정상 로그인·회원/주문 조회·상담 생성/이력·AI_DISABLED를 실제 HTTP로 확인했다.** 같은 비활성 상태의 데스크톱 웹 로그인·주문 목록도 확인했다.

일반 앱도 AI 설정을 생략하면 기능이 꺼진 기본값으로 실행된다. AI용 키 때문에 기존 앱 부팅을 막지 않는다. 다만 일반 앱의 기존 MySQL 등 필수 의존까지 없애는 것은 아니며, 아래 전용 실행기는 그 DB를 임시 컨테이너로 준비해 주는 별도 경로다.

## 현재 최종 동의 기준

CANCEL 최종 질문에서 텍스트로 동의할 때는 **모델 AFFIRM + 서버 `final-consent-v2` EXPLICIT**을
모두 통과해야 취소 요청으로 진행한다. `CONFIRM_CANCEL` 버튼 경로도 지원한다.
규칙의 결과·버전은 턴의 `toolSummary`에만 남기고, 업무 이력에는 기존 TEXT/BUTTON 출처만 저장한다.
현재 프롬프트는 `cancel-v10.7-order-dialogue`이다. 모델 해석만으로 취소를 실행하지 않는다.

## 1. 사용자가 직접 API 키 입력

프로젝트 루트의 `server/.env.local`을 에디터로 열고 다음 빈칸만 채운다.

```dotenv
OPENAI_API_KEY=여기에_본인_API_키
```

이 파일은 `.gitignore`로 제외하고 파일 권한은 `600`으로 둔다. 공유할 양식은 `server/.env.example`이며 양식에는 실제 키를 쓰지 않는다. 파일이 없는 새 체크아웃에서는 **기존 파일을 덮어쓰지 않고** 양식을 복사한다.

```sh
cp -n server/.env.example server/.env.local
chmod 600 server/.env.local
```

키를 채팅·스크린샷·명령 인자에 붙이지 않는다. 실행기가 파일을 문자 데이터로 읽어 자식 프로세스의 `OPENAI_API_KEY`로만 전달한다. `source`, `eval`, 변수 치환은 하지 않는다. AI가 꺼진 실행은 키 파일을 읽지 않는다. 이전 `local-docs/ai-support/local-openai.env`는 자동으로 읽거나 이동·삭제하지 않는다. 이전 파일에 입력했다면 사용자가 직접 새 파일을 정리한다.

## 2. 기본 실행은 AI 끔

다음 명령은 모두 **프로젝트 루트**에서 실행한다. 이 터미널은 서버가 켜진 동안 유지된다.

```sh
python3 server/scripts/local-support-web.py start
```

기본 주소는 `http://127.0.0.1:18080`이다. 포트가 사용 중이면 실패하며 다른 프로세스를 종료하지 않는다. 필요한 경우 `--port 18081`처럼 명시하고 프런트 프록시 주소도 맞춘다. `LOCAL_SUPPORT_READY` 또는 아래 `status` 결과를 확인한 뒤 접속한다.

```sh
python3 server/scripts/local-support-web.py status
```

합성 로그인은 다음과 같다. 실서비스용 비밀번호가 아니다.

| 항목 | 값 |
|---|---|
| 아이디 | `eval-buyer` |
| 비밀번호 | `LocalSupport123!` |
| 예시 상품 | `평가 배송전침대` |
| 예시 질문 | `평가 배송전침대 주문 취소 가능해?` |

기존 평가 fixture를 빈 전용 DB에 넣고 구매자 1명만 BCrypt 비밀번호를 설정한다. 판매자/타인 계정은 정상 로그인용으로 바꾸지 않는다. AI가 꺼져 있을 때는 로그인과 상담 기록 API를 확인할 수 있고 새 모델 요청은 `AI_DISABLED`로 거절된다.

## 3. 별도 터미널에서 웹 실행

```sh
cd client
SETTY_API_URL=http://127.0.0.1:18080 \
SETTY_POSTHOG_KEY= SETTY_FIREBASE_API_KEY= SETTY_FIREBASE_PROJECT_ID= \
SETTY_FIREBASE_MESSAGING_SENDER_ID= SETTY_FIREBASE_APP_ID= SETTY_FIREBASE_VAPID_KEY= \
npm run dev -- --host 127.0.0.1 --port 4174
```

데스크톱 브라우저에서 `http://127.0.0.1:4174/support`를 열어 합성 계정으로 로그인한다. 기존 `client/.env`는 수정하지 않는다. 4174가 이미 사용 중이면 다른 빈 포트를 선택한다. 웹 개발 서버는 이 터미널의 Ctrl+C로 종료한다. 모바일 화면은 이번 검증 범위가 아니다.

## 4. 실제 모델 사용

기본 실행을 먼저 종료한 뒤, 키 입력과 비용/질문 범위를 확인했을 때만 아래 명령을 사용한다.

```sh
python3 server/scripts/local-support-web.py stop
python3 server/scripts/local-support-web.py start --enable-ai --model gpt-6-luna
```

복원 옵션 없이 시작하면 DB가 새로 만들어지므로 다시 로그인한다. 모델은 OpenAI `gpt-6-luna`이며 dev의 Bedrock으로 자동 전환하지 않는다. 사용자의 요청으로 **로컬 서버 전체의 누적 호출 제한은 제거했다.** 현재 v9의 대화 경로는 모델 최대 2회·업무 조회 최대 1회·60초 기한, 모델 응답 토큰 제한과 자동 재시도 없음을 유지한다. 최종 동의 후 취소 요청은 모델 호출 없이 별도 서버 경로로 실행한다. 계속 질문하면 비용이 누적되며 고정 금액 상한은 보장하지 않는다.

호출 시도 수·알려진 입출력 토큰·모델명·사용량 미확인 여부는 `server/build/local-support-web/model-usage.json`에 남긴다. 키·프롬프트·응답 본문은 이 파일에 기록하지 않는다. timeout 뒤 응답을 받지 못했다면 `usageUnknown=true`이며 과금 0을 뜻하지 않는다. `maxProviderCallAttempts: null`은 전체 실행의 누적 제한이 없다는 뜻이다. 기본 포트 외에는 `server/build/local-support-web-<포트>/`에 같은 파일을 남긴다.

## 5. 종료와 데이터 수명

프로젝트 루트에서:

```sh
python3 server/scripts/local-support-web.py stop
```

18081처럼 다른 포트로 시작했다면 `status --port 18081`, `stop --port 18081`로 같은 실행을 지정한다.

전용 중지 파일을 통해 해당 앱과 그 앱이 만든 Testcontainer만 종료한다. 기존 서버·DB는 종료하지 않는다. 시작 터미널의 Ctrl+C도 종료 방법이다. 합성 주문과 상담 기록은 전용 컨테이너 종료와 함께 사라지므로 필요한 증거는 종료 전 확인한다. 24시간 스케줄러 지연은 모든 배치의 영구 비활성화가 아니므로 이 서버를 장기 운영하지 않는다.

## 유료 호출 없는 코드 검증

```sh
cd server
python3 -m unittest discover -s scripts/tests -p 'test_local_support_web.py'
./gradlew test --tests 'setty.aisupport.LocalSupportModelBudgetTest'
```

별도 기존 `supportSmokeOffline` 작업은 MockMvc + 실제 도메인 DB + 가짜 모델 경계를 검증한다. `liveSupportSmoke`는 별도 승인 대상인 Bedrock smoke이며 이 OpenAI 웹 실행 명령과 섞지 않는다.

## 확인한 실제 웹 범위 (2026-11-04)

사용자 승인 후 새 격리 DB와 `gpt-6-luna`로 예시 질문 **1개**를 웹에서 전송했다. 실제 공급자 호출 3회, 입력 2,557·출력 118토큰을 관측했고 `find_my_orders`와 `get_order_support_context`가 모두 성공했다. 주문 930003의 현재 취소 가능 여부가 서버의 고정 안내로 표시됐으며 상담 1건이 `SUCCEEDED`로 저장됐다. 새로고침·상담 목록에서 재진입해 같은 결과를 확인했다.

조회 전후 합성 주문 28행의 주문 ID·구매자·주문 상태·주문에 복제된 배송 상태·실제 배송 상태·판매 상태가 동일했다. 전체 필드나 모든 업무 시나리오를 검사한 결과는 아니다. 키 내용은 출력하지 않았고 추가 질문을 전송하지 않았다. 이 한 사례의 연결 성공을 전체 품질 평가나 실제 사용자 편의성 입증으로 확대하지 않는다. 확인 후 전용 앱과 임시 DB를 종료했다.

## 기록을 보존하는 로컬 전환

일반 `stop`은 임시 DB도 종료하므로 기록을 보존하려면 먼저 별도의 보호된 SQL 백업이 필요하다. 이미 검증한 소유 로컬 DB의 백업만 `server/build/` 아래 권한 `600`으로 보관하고 다음처럼 복원할 수 있다. 백업에는 상담 내용과 로그인 토큰이 있으므로 출력하거나 공유 증거로 복사하지 않는다.

```sh
python3 server/scripts/local-support-web.py start --port 18081 --enable-ai --model gpt-6-luna --restore-from server/build/local-support-backups/before-limit-removal-final.sql
```

복원은 새 전용 컨테이너에만 적용하며 기존 시드나 비밀번호를 다시 설정하지 않는다. 복원·health·데이터 동일성을 확인한 뒤 프런트의 `SETTY_API_URL`을 새 포트로 바꾸고 원본 실행을 종료한다. 백업 생성 중에는 쓰기를 잠시 멈춰 마지막 질문까지 보존해야 한다. 위 경로는 이번 전환에서 만든 로컬 파일이며 새 체크아웃에는 없다.

2026-11-04 누적 제한 제거 전환에서는 원본 쓰기를 멈춘 뒤 최종 백업을 만들고, 새 18081 DB의 전체 덤프 해시가 원본과 같은 것을 확인했다. 기존 로그인과 상담 기록을 유지한 웹에서 새 질문이 성공했고, 실제 모델 2회·입력 1,697/출력 152토큰과 `find_my_orders` 성공을 관측했다. 이전 18080 앱과 그 소유 컨테이너만 종료했으며 새 실행은 사용자 테스트용으로 유지했다.


## 정책·대화 기반 상담 (2026-11-04 후속)

`src/main/resources/aisupport/instructions.md`와 `cancellation-policy.md`를 실행 자원으로 포함한다. 역할 지침·정책·최근 대화·도구 결과를 모델에 전달하며, 최종 답변을 고정 템플릿으로 덮어쓰지 않는다. 인사/일반 정책은 바로 응답하고 주문 정보가 필요할 때 조회 도구를 사용한다. 주문 카드의 서버 판정과 본인 주문 권한 검사는 유지한다. 정책 변경 시 실행용 요약과 도메인 규칙을 함께 검토한다.

같은 포트로 종료 직후 다시 시작할 때 TIME_WAIT을 허용하도록 포트 검사 소켓에 SO_REUSEADDR를 사용한다. 실행 중인 리스너는 여전히 거절한다. SQL 백업은 외래키 생성 순서를 고려해 복원할 수 있는 형식이어야 한다. 이번 compact 덤프는 보호 복사본의 시작/끝에 FOREIGN_KEY_CHECKS=0/1을 적용한 뒤 전체 덤프 해시 동일성을 확인했다.


## 상담 범위 분류 실험

`python3 scripts/evaluate-support-scope.py --model gpt-6-luna --output build/scope-eval/new-run.jsonl`은 합성 질문으로 별도 유료 비교를 수행한다. 기존 웹/DB를 사용하지 않고 실제 SupportRuntime/OpenAI 어댑터에 합성 주문 reader를 연결한다. 일반 test/check에서는 실행하지 않는다.

비교 조건/루브릭은 `src/test/resources/aisupport/scope-eval/README.md`에 둔다. 별도 분류기는 테스트 코드에만 있으며 실제 앱에는 설치하지 않았다. 당시 앱 프롬프트 v4에서는 범위를 구체화했지만 웹에서 범위 이탈이 남아 있었다. '일반 검사 통과'나 '합성 질문 거절'만으로 의미상 범위 준수가 보장되는 것은 아니다.

## 현재 앱 처리 흐름 (2026-11-05, v6)

`SupportRuntime`은 자연어를 `SupportModel.plan`으로 구조화한 뒤 서버에서 검증·분기한다. 범위 밖 요청은 서버 안내문, 주문 검색은 서버 날짜·목록을 사용한다. 특정 주문 상태는 서버 조회 후 도구 없는 설명 호출로 처리한다. 카드 선택은 계획 호출을 생략한다. 기존 카드에는 `createdDateKst`가 없어도 표시할 수 있다. API 키 없이 AI 비활성 실행하는 기본값은 유지한다.

OpenAI는 strict JSON schema, Bedrock은 `support_plan` 강제 도구 선택으로 중립 계약을 반환한다. 실제 OpenAI 웹 8턴 및 오프라인 SDK 계약을 검증했다. 이번 변경의 실제 Bedrock 호출·dev 배포는 하지 않았다. 과거 단일 CLI 호출 성공과 구분한다.

## 주문 탐색 v7

주문 내역(`/me/orders`)의 상담 버튼으로 시작한다. 주문 카드의 상세 보기는 기존 주문 상세로 이동하고, 취소 가능 여부 확인은 별도 조회다. 검색은 기본 5건·요청에 따라 최대 20건이며 더 보기 버튼은 모델 없이 저장된 검색 조건으로 조회한다. 자연어 다음 요청은 분류 호출 1회가 필요하다. 구조화 계획은 `pageSize`, `purpose`를 포함한다. 기존 저장 카드도 읽을 수 있으며 새 필드는 새 조회부터 표시한다.

## 최근 주문 확인 v8

최근 주문 취소 질문은 주문일이 확인되는 최신 1건을 먼저 제시한다. 맞아요 버튼 또는 대화상 확인 후 해당 주문의 취소 가능 여부를 조회한다. 날짜 없는 주문이 있으면 최신 여부를 한정해 안내한다. 기존 주문 탐색 목록은 그대로 지원하며 v8까지는 실제 취소 실행을 포함하지 않았다.


## 최종 동의 후 서버 취소 요청 v9

취소 가능 안내 → 취소 요청하기 → 주문/접수·환불 안내 검토 → **동의하고 취소 요청** 순서로 진행한다. 대화의 “응”만으로 실행하지 않는다. 모델 계획/도구에는 실행 액션이 없다. 인증된 `CONFIRM_CANCEL` 요청만 전용 서버 경로로 처리한다.

서버는 본인 대화의 최신 취소 가능 카드(5분 유효)와 주문 번호를 검증하고 주문을 잠근 뒤 상태를 재조회한다. 기존 `OrderService.requestCancellation`을 호출하며, 상태 변경·기존 이벤트·접수 턴을 같은 트랜잭션에 저장한다. 같은 확인 카드의 재전송은 기존 영수증을 반환한다. UI에는 접수와 최종 확정/환불 완료를 구분한다. 이전 상담 카드는 보존하며 새 조회부터 실행 버튼이 생긴다.

기본 AI 비활성 실행은 유지하며 일반 기능에 API 키를 요구하지 않는다. 웹 검증은 원본 전체 백업에서 분리한 별도 DB에서 수행해야 한다. Testcontainers의 JVM 종료는 DB도 삭제하므로 사용자 대화가 있는 원본을 백업 없이 종료하지 않는다.


## 2026-11-06 v10 기본 흐름 변경

현재는 상품별 최근 미취소 주문 한 건을 확인한 뒤, 가능 여부와 별도의 최종 동의를 거쳐 서버에서 취소를 요청한다. 자연어와 버튼을 모두 지원하며 첫 번째 긍정은 취소를 실행하지 않는다. V17은 활성 질문, V18은 상담과 독립된 최소 업무 결과를 저장한다. `GET /api/support/cancellations/{requestId}`는 모델 없이 소유권을 확인해 결과를 반환한다. 웹은 최대 30초 자동 조회 후 재확인 버튼을 제공한다.

합성 fixture도 실제 등록과 동일하게 `delivery_order_decision`을 넣어야 한다. 이 이력이 없으면 이미 배송 행이 있어도 등록 전 취소 분기로 들어가 잘못된 검증이 된다. `SupportEvaluationFixtureTest`가 이 불변식을 검사한다. 기존 서버/DB를 건드리지 않고 별도 포트와 Testcontainer에서 검증한다.

검사: `./gradlew test`, `./gradlew supportSmokeOffline`, client의 `npm test`와 `npm run type-check`. 테스트 통과가 실제 모델 의미 평가나 Bedrock 통합 성공을 대신하지 않는다. 개인 학습/작업 기록은 프로젝트 루트의 git 제외 `local-docs/ai-support/cancellation-flow-implementation.md`에 있으며 코드 실행에는 필요하지 않다.

## 로컬 AI 상담 데스크톱 검증

로컬은 OpenAI API, dev 배포는 기존 Bedrock을 유지한다. API 키는 서버의 git 제외 파일 `server/.env.local`에만 두며 프론트 `.env`나 브라우저에 넣지 않는다. 키 없는 공유 템플릿은 `server/.env.example`이다. 기존 client 환경파일은 수정하지 않는다.

현재 웹은 정상 로그인, 주문 조회·취소 가능 여부 안내, 최종 동의 버튼을 통한 서버 취소 요청을 지원한다. CANCEL 최종 질문의 텍스트 동의는 모델 AFFIRM과 서버 `final-consent-v2`의 EXPLICIT 판정을 모두 통과해야 취소 요청으로 진행한다. 최종 동의 버튼 경로도 지원한다. 환불 실행이나 모바일/반응형 별도 검증은 포함하지 않는다. 아래는 키 없이 AI 비활성 상태로 시작하는 방법이다.

먼저 Java21·로컬 Docker가 준비돼 있어야 한다. 전용 백엔드 실행기는 새 합성 MySQL을 만들고 종료할 때 정리하며 기존 dev/prod DB를 사용하지 않는다. 저장소 루트에서 별도 터미널로 실행한다.

```sh
python3 server/scripts/local-support-web.py start --port 18080
```

`LOCAL_SUPPORT_READY http://127.0.0.1:18080 AI_ENABLED=false`를 기다린다. 포트가 이미 사용 중이면 실행기가 거부하므로 기존 프로세스를 종료하지 말고 다른 `--port`를 선택한다.

다른 터미널에서 웹 포트가 비어 있는지 확인한다. 출력이 있으면 기존 프로세스를 그대로 두고 다른 포트를 선택한다. 출력이 없더라도 실행 시점 충돌은 가능하다.

```sh
lsof -nP -iTCP:4174 -sTCP:LISTEN
```

`client` 디렉터리에서 실행한다. 백엔드 포트를 바꿨다면 `SETTY_API_URL`도 맞춘다.

```sh
SETTY_API_URL=http://127.0.0.1:18080 \
SETTY_POSTHOG_KEY= \
SETTY_FIREBASE_API_KEY= \
SETTY_FIREBASE_PROJECT_ID= \
SETTY_FIREBASE_MESSAGING_SENDER_ID= \
SETTY_FIREBASE_APP_ID= \
SETTY_FIREBASE_VAPID_KEY= \
npm run dev -- --host 127.0.0.1 --port 4174
```

`http://127.0.0.1:4174/support`에 접속하고 합성 계정 **`eval-buyer` / `LocalSupport123!`**로 정상 로그인한다. 이 값은 새 테스트 DB 전용이며 실제 서비스 계정이 아니다. 로그인은 비밀번호 BCrypt 검사 뒤 토큰을 회전하므로 fixture의 정적 API 토큰을 브라우저에 주입하지 않는다.

AIoff에서는 새 상담과 기록 조회가 가능하며 메시지는 비활성 안내로 거절되고 실제 모델 호출은 없다. API 키를 설정했다고 자동으로 AI가 켜지지 않는다. 실제 호출은 비용/범위를 승인한 뒤 서버를 `--enable-ai --model gpt-6-luna`로 시작하는 별도 단계다. 추가 질문/후보 클릭도 새 모델 호출을 만들 수 있어 승인한 턴 범위만 수행한다.

종료할 때 웹 터미널은 Ctrl-C, 백엔드는 실행 터미널의 Ctrl-C 또는 저장소 루트에서 아래 명령을 사용한다.

```sh
python3 server/scripts/local-support-web.py stop
```

이 준비 절차 자체가 실제 서버/웹 검증 성공을 뜻하지 않는다. 실제 실행 결과는 별도로 기록한다.

## 중간 기록 (대체됨)

2026-11-09 작업 1 직후의 기록이다. 위 현재 기준으로 대체했으며 당시 검증 이력은 보존한다.

## 최종 동의 서버 규칙 — 2026-11-09 작업 1

현재 CANCEL 최종 질문의 텍스트 동의는 **모델 AFFIRM + 서버 FinalConsentRule EXPLICIT**을 모두
만족해야 취소 실행 경로로 진행한다. 규칙 버전은 `final-consent-v1`이며 프롬프트 버전은 변경하지 않았다.
ORDER 대상 확인, `CONFIRM_CANCEL` 버튼, ANSWER/CLARIFY 재확인 경로는 기존 동작을 유지한다.

- 허용: `네/예/응/맞아요/좋아요/그래요` 단독, 또는 뒤에
  `취소해 주세요/취소해주세요/취소할게요/취소해줘/진행해 주세요/진행해주세요`가 붙은 답변.
- 공백·문장 끝 장식·이모지를 정규화하되, 질문·부정·조건·유보 표현과 정규화 후 25자 초과 입력은 거부한다.
  허용 표현 밖의 자연스러운 동의도 버튼 재확인으로 돌아갈 수 있다. `취소해줘` 단독도 현재 허용 목록 밖이다.
- 모델이 `네?`나 `네 근데 환불은 언제야`를 AFFIRM으로 잘못 분류해도 서버는 실행하지 않는다.
  같은 주문을 재조회해 여전히 가능하면 새 확인 버튼을 보여주고, 불가 상태면 실행 버튼을 제시하지 않는다.
- 턴 감사 기록에는 `final_consent_rule`의 결과·버전만 추가한다. 기존 업무 이력의 TEXT/BUTTON은 유지한다.
  **사용자 결정으로 스키마·업무 이력 버전 저장은 보류했다.** 대화 삭제·만료 시 규칙 감사 기록도 삭제된다.
  업무 이력에 규칙 버전을 저장할지는 PR 준비 때 결정한다.

허용·거부 표는 `server/src/test/java/setty/aisupport/FinalConsentRuleTest.java`,
모델 오분류 시 실행 차단·감사·재확인은 `SupportBasicFlowIntegrationTest.java`에서 검사한다.
검토 보고서와 단계별 증거는 Git 제외인 `local-docs/ai-support/review/`에 보관한다.
검증: 규칙 표 59건, 추가 통합 19건. 서버 전체는 기준선 1,015 통과/0 실패/1 건너뜀에서
1,093 통과/0 실패/1 건너뜀으로 늘었고 기존 검사 제거는 없다. 웹 38건·타입 검사도 전후 통과했다.
이번 작업은 자동 검증이며 실제 모델 평가·브라우저 확인이나 실행 중 앱 재시작은 수행하지 않았다.
