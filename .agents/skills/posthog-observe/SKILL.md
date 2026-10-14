---
name: posthog-observe
description: 세티 PostHog의 이벤트 수집, 계정 식별, 코호트 필터, WAU와 결제 전환율을 관찰·대조하거나 수치 불일치를 진단할 때 사용한다.
---

# 세티 PostHog 관찰

수집된 이벤트와 지표를 근거로 결과를 설명한다. 기존 데이터 관찰을 기본으로 하고, 새 트래픽 생성·결제·서버 설정 변경은 사용자가 요청한 범위에서 수행한다.

## 관찰 순서

1. **조건을 맞춘다.** 프로젝트, 시작·종료 시각, 시간대, 계정 코호트, dev/prod, 실제·모의·가상 트래픽과 스모크 포함 여부를 기록한다. [기존 대시보드](https://us.posthog.com/project/640788/dashboard/2196165)의 현재 필터를 직접 확인한다. 접근할 수 없으면 필터가 보이는 캡처와 테스트 요약을 받아 확인하며, 보이지 않는 설정은 `확인 필요`로 남긴다.
2. **이벤트부터 확인한다.** Activity의 Events에서 같은 기간의 한 계정을 검색한다. `distinct_id`는 로그인 ID이며 `$identify`와 대상 행동 이벤트를 확인한다. Identify·Web vitals만으로 구매 퍼널 수집을 판단하지 않는다. 이벤트와 속성은 [수집 코드](../../../client/src/analytics/posthog.ts), [상품 행동](../../../client/src/components/ProductDetail.tsx), [결제 복귀](../../../client/src/payment/usePaymentReturn.ts)를 기준으로 확인한다.
3. **코호트를 대조한다.** 이벤트가 보이는지 먼저 확인한 뒤 테스트 계정 코호트를 적용한다. 적용 후 사라지면 프로젝트·기간·구성원 매칭을 점검한다. CSV 가져오기에는 PostHog에 이미 식별된 사람 프로필이 필요하며, 로그인 ID만 한 줄씩 넣는다. DB 계정 생성만으로는 부족하다.
4. **지표 설정을 확인한다.** 아래 이벤트와 고유 사용자 기준을 사용한다. 모든 카드의 기간·코호트·시간대를 맞추고 퍼널의 발생 순서와 전환 시간 제한을 기록한다. 기본 예시 `pageview → autocapture`나 카드 제목만으로 설정을 판단하지 않는다.
5. **기대값과 관측값을 나눈다.** k6 결과가 있으면 [시나리오·기대값](../../../scripts/payment-conversion/README.md)과 비교한다. 재조회는 이벤트 수를 늘리지만 고유 사용자를 늘리지 않는다. 같은 계정의 스모크·재실행이 집계에 섞이는지 먼저 확인한다.

| 지표 | 설정 |
| --- | --- |
| WAU(주간 활성 사용자) | 한 주 전체의 `product_viewed` 고유 사용자 수. Trends의 `Unique users`·`Total value`로 확인하며 일별·시간별 수치를 합산하지 않는다. |
| 조회→결제 전환율 | 고유 사용자 퍼널 `product_viewed → payment_succeeded` |
| 결제창→결제 전환율 | 고유 사용자 퍼널 `checkout_opened → payment_succeeded` |
| 결제창 닫기 | `checkout_closed`. 구매 포기나 재확인 의도를 이 이벤트만으로 단정하지 않는다. |

`is_mock`은 MSW 모의 데이터 구분이며 dev/prod 구분이 아니다. dev API를 사용해도 PostHog 프로젝트가 자동 분리되지 않으므로 계정 코호트와 실행 기간으로 대조한다.

코호트 CSV가 필요하면 저장소 루트에서 준비된 구매자 CSV를 사용한다. 아래 입력 경로는 실제 파일로 바꾸고 비밀번호 열은 업로드하지 않는다.

```sh
awk -F, 'NR > 1 { print $1 }' .local/k6-test-users-milan.csv \
  > .local/k6-milan-loginids.csv
```

## 문제와 확인 순서

| 관측된 문제 | 먼저 확인할 것 |
| --- | --- |
| 이벤트가 없음 | 올바른 프로젝트·기간 → 브라우저 Network의 수집 요청 → `SETTY_POSTHOG_KEY`·`SETTY_POSTHOG_HOST` 빌드 반영 → 광고·자동화 차단. 환경변수 값은 출력하지 않는다. |
| CSV의 ID가 전부 매칭되지 않음 | 같은 프로젝트의 식별된 프로필·`distinct_id`·CSV 형식. 헤더 하나만 제거하면 해결된다고 단정하지 않는다. |
| k6의 PostHog 응답 대기 | 요청 관측·HTTP 응답·Activity 저장을 각각 확인한다. HTTP 2xx는 접수 확인이며 최종 집계는 대시보드에서 대조한다. |
| 토스 인증 후 메인 복귀, 결제는 대기 | 서버 승인 `DONE`과 같은 주문·매물의 `CONFIRMED`를 확인한다. 화면 복귀나 HTML 200만으로 승인 성공을 판단하지 않는다. |
| 전체 실행에서 구매가 시작되지 않음 | 선행 `pre_purchase` 오류·임계값. 선행 실패는 구매 단계를 중단한다. 확인 이벤트 수 감소는 후속 검증을 건너뛴 결과일 수 있다. |
| 기대 20%·40%와 관측값이 다름 | 기간·코호트·스모크·반복 실행·퍼널 조건. 기대 수치에 맞춰 관측값을 수정하지 않는다. |

k6 진단은 [실행·진단 문서](../../../scripts/payment-conversion/test-data-setup.md)를 따른다. 실제 PostHog 수집 시 `K6_BROWSER_HEADLESS=false`와 기존 관측 모듈을 사용한다. SDK가 자동화로 차단했는지 확인한 뒤 원인을 설명한다. 승인 키를 추측해 교체하거나 운영 수집 설정을 바꾸는 해결책부터 제시하지 않는다.

## 결과 기록

`조건 → 기대값 / 관측값(고유 조회자·진입자·완료자·두 전환율) → 확인된 문제·원인과 해결 → 미확인 범위` 순서로 짧게 작성하고, 기간·필터가 보이는 캡처 또는 이벤트·로그 근거를 연결한다. 원인을 확정하지 못했으면 `확인 필요`로 남긴다.

가상 트래픽은 수집·집계·결제 흐름 검증의 근거다. 사용자 의도와 자발적 재시도율 상승의 근거로 쓰지 않는다. 현재 시나리오의 재확인 10명은 다시 조회하고 종료하므로 재확인 후 결제 재시도 흐름은 미검증이다. 관련 가설의 효과는 판단 보류로 기록한다.

## 팀 사용·공유

공유 ZIP의 `posthog-observe/` 폴더를 세티 저장소의 `.agents/skills/` 아래에 둔다. 팀원은 PostHog 프로젝트 접근 권한을 확보하고 해당 저장소에서 `$posthog-observe 이번 실행의 WAU와 결제 전환율을 확인해줘`로 호출한다. 인식되지 않으면 Codex를 다시 시작하거나 이 `SKILL.md`를 직접 읽도록 요청한다. [공식 스킬 안내](https://learn.chatgpt.com/docs/build-skills)

공식 기준: [CSV 코호트](https://posthog.com/docs/data/cohorts), [수집 문제·자동화 차단](https://posthog.com/docs/product-analytics/troubleshooting), [Trends](https://posthog.com/docs/product-analytics/trends/overview), [Funnels](https://posthog.com/docs/product-analytics/funnels).
