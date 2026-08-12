# 배차 운영자 화면 구현 경계

- 적용 Issue: GitHub Issue #28
- 기준: `develop`의 배차 운영자 조회 API와 DEC-017·022
- 목적: 운영자가 `curl` 없이 배차 요청 목록과 상세를 확인한다.

## 라우트와 소유 경계

```text
/operator/login
/operator/estimate-requests
/operator/estimate-requests/:id
/operator/dispatch-requests
/operator/dispatch-requests/:id
```

- 공통 로그인·셸은 `src/flows/operator/auth/**`, `shell/**`가 소유한다.
- 배차 운영 화면은 `src/flows/operator/dispatch/**`가 소유한다.
- 사용자용 `src/flows/dispatch/**`를 import하거나 수정하지 않는다.
- `/operator`의 기존 기본 이동은 견적 목록으로 유지한다. 중립 운영 홈은 별도 제품 결정 전 추가하지 않는다.

## 이번 구현에서 사용하는 서버 계약

| 목적          | 계약                                                          |
| ------------- | ------------------------------------------------------------- |
| 비밀번호 확인 | `GET /api/operator/auth`                                      |
| 배차 목록     | `GET /api/operator/dispatch-requests?status={DispatchStatus}` |
| 배차 상세     | `GET /api/operator/dispatch-requests/{id}`                    |

모든 요청은 현재 탭에 저장한 운영자 비밀번호를 `X-Operator-Secret` 헤더로 보낸다. `401`이면 저장값을 지우고 로그인 화면으로 이동한다.

목록의 최신순은 서버 응답을 신뢰하며 프론트가 다시 정렬하지 않는다. 상세의 `seller: null`과 운영 기록의 nullable 필드는 오류가 아니라 아직 기록되지 않은 정상 상태로 표시한다.

## 현재 가능한 운영 결과

- 견적 요청: 기존 API로 실제 문자 발송 결과를 저장하고 상태를 변경할 수 있다.
- 배차 요청: 목록·상세·상태 필터·판매자 입력 링크 복사만 가능하다.
- 배차 상세의 최종 금액·확인 시각·운영 메모·종료 사유는 서버 응답이 있을 때 읽기 전용으로 표시한다.

## 의도적으로 구현하지 않는 동작

- 배차 상태 변경
- 최종 금액·운영 메모·종료 사유 저장
- 실제 발송 SMS 원문 저장
- 구매자 최종 금액 동의·거절
- 삭제

현재 서버에 위 쓰기 계약이 없으므로 프론트에서 성공한 것처럼 보이는 임시 동작이나 브라우저 전용 저장을 만들지 않는다.

## 후속 서버 계약이 필요한 기능

배차 운영자가 최종 금액을 문자로 안내한 뒤 기록하려면 최소한 다음 서버 동작이 필요하다.

```text
최종 금액과 실제 발송 문자 원문 저장
→ 서버 기록 시각 저장
→ 허용된 다음 상태로 변경
```

내부 `operatorNote`와 사용자에게 실제로 보낸 `messageContent`는 의미와 보관 목적이 다르므로 같은 필드로 사용하지 않는다. 허용 상태 전이와 변경 주체는 Issue #29에서 합의한 뒤 쓰기 UI를 별도 Issue로 추가한다.
