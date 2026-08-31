# 배송 문서 인덱스

`setty.delivery`의 설계 문서와 ADR 진입점이다. 구현을 변경할 때 결정의 종류에 맞는 문서만 수정한다.

## 문서 지도

| 문서 | 원본으로 관리하는 내용 |
|---|---|
| [배송 도메인 설계](domain-design.md) | Aggregate, VO, 객체 관계, 상태와 불변식 |
| [배송 모듈 구조](module-architecture.md) | API, Application, 이벤트, Repository, Persistence 구조 |
| [배송 작업 규칙](../AGENTS.md) | 에이전트가 작업할 때 지켜야 할 소유권·금지사항·검증 규칙 |
| [ADR-0001](adr/0001-delivery-aggregate-boundary.md) | Delivery Aggregate 경계와 ID 참조 결정 |
| [ADR-0002](adr/0002-synchronous-order-status-sync.md) | Delivery와 Order 상태의 동기 트랜잭션 결정 |
| [서버 예외 처리 규칙](../../../../../../docs/exception-handling.md) | `BusinessException`, `ErrorCode`, 오류 응답의 서버 공통 원본 |

## 변경 라우팅

### `domain-design.md`

다음 변경만 기록한다.

- 새로운 Domain 객체 또는 VO
- Aggregate 포함 관계 변경
- 상태 전이와 불변식 변경
- 식별자·동등성·시간 규칙 변경

Application Service, Controller, Repository 기술과 테스트 실행법은 기록하지 않는다.

### `module-architecture.md`

다음 변경을 기록한다.

- 패키지와 계층 의존 방향
- 이벤트 발행·처리 구조
- 트랜잭션 경계
- Command·Query Repository 역할
- API·인증·영속성 경계

### `AGENTS.md`

반복 작업에서 반드시 지켜야 하는 규칙만 둔다. 구현 현황 설명이나 설계 근거를 복사하지 않고 해당 문서에 링크한다.

### ADR

비용이 있는 선택, 거부한 대안 또는 되돌리기 어려운 결정을 기록한다.

```text
adr/0001-kebab-case-title.md
adr/0002-kebab-case-title.md
```

- 번호는 순차 증가시킨다.
- 한 ADR에는 한 가지 핵심 결정을 기록한다.
- 상태, 맥락, 결정, 근거, 결과, 거부한 대안, 검증을 포함한다.
- 이미 채택된 ADR의 결론을 바꿀 때는 기존 파일을 덮어쓰지 않고 새 ADR로 대체 관계를 기록한다.

## 동기화 규칙

- Domain 코드 변경 → `domain-design.md` 확인
- 이벤트·트랜잭션·Repository 변경 → `module-architecture.md` 확인
- 새로운 장기 규칙 → `AGENTS.md` 확인
- 새로운 설계 선택 → ADR 발행 여부 확인
- 문서 링크 변경 → 이 인덱스와 연관 문서의 역링크 확인
