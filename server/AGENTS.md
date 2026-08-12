# SETTY server guidance

루트 `AGENTS.md`와 함께 적용한다.

## Confirmed environment

- Java 21
- Spring Boot, Gradle Wrapper
- 실행: `./gradlew bootRun`
- 테스트: `./gradlew test`
- 빌드: `./gradlew build`

JPA와 MySQL을 사용한다. 첫 MVP는 가상 데이터 기반 사용자 행동 검증을 위해 JPA의 `ddl-auto=update`로 개발 스키마를 만든다. Flyway는 MVP 검증 후 도입 여부를 재검토한다.

## Before editing

- `docs/product/user-operation-flow.md`, `docs/product/mvp-scope.md`, 관련 DEC와 Issue를 읽는다.
- 필수 입력·상태·권한·보관 정책을 코드로 임의 확정하지 않는다.
- 현재 Issue에 필요하지 않은 자동화와 일반화를 추가하지 않는다.

## Domain boundaries

- 예상 견적과 배차 요청은 서로 다른 요청과 상태를 가진다.
- 견적 BE는 견적 생성·검증·저장과 운영자 조회를 맡는다.
- 배차 BE1은 구매자 요청과 판매자 입력 링크·세션을 맡는다.
- 배차 BE2는 판매자 입력 저장, 양쪽 정보 결합과 운영자 조회를 맡는다.
- 배차 BE1·BE2는 공통 모델·상태·트랜잭션·API 계약을 코드 전에 합의한다.
- 가격·차량·운송 가능 여부는 운영자가 입력한 결과로만 저장한다.
- SMS·운송사 호출·실제 배차는 외부 수동 업무다.

## Security

- 실제 개인정보·접근 토큰·DB 접속정보를 로그에 남기지 않는다.
- 오류 응답에 내부 스택·운영 메모·상대방 정보를 포함하지 않는다.
- 구매자·판매자·운영자 응답을 분리한다.
- 관리자 인증, 사용자 링크 만료와 개인정보 삭제가 미정이면 실제 공개를 완료로 표시하지 않는다.

## Verification

- 정상·4xx·DB 실패·중복·권한·허용되지 않은 상태를 테스트한다.
- 역할별 응답에 불필요한 개인정보가 없는지 확인한다.
- 변경 후 관련 테스트와 `./gradlew build`를 실행한다.
